package com.mcgg.fasthand

import android.app.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import android.os.SystemClock
import android.util.DisplayMetrics
import android.view.WindowManager
import android.graphics.RectF
import java.nio.ByteBuffer

/**
 * Continuous, user-consented screen analysis.
 * Frames are converted to Bitmaps in memory and discarded after on-device ML Kit OCR.
 * No screenshot files are created. Frames are analyzed only while MCGG is foreground.
 */
class ScreenCaptureService : Service() {
    companion object {
        const val ACTION_START = "com.mcgg.fasthand.SCREEN_CAPTURE_START"
        const val ACTION_CAPTURED = "com.mcgg.fasthand.SCREEN_CAPTURED"
        const val ACTION_ANALYSIS = "com.mcgg.fasthand.SCREEN_ANALYSIS"
        const val ACTION_STOP = "com.mcgg.fasthand.SCREEN_CAPTURE_STOP"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_IMAGE_URI = "image_uri" // Kept for backward compatibility; no longer emitted.
        const val EXTRA_ANALYSIS = "analysis"
        const val EXTRA_GAME_ACTIVE = "game_active"
        private const val CHANNEL = "mcgg_screen_capture"
        private const val NOTIFICATION_ID = 52
        private const val ANALYSIS_INTERVAL_MS = 1400L
    }

    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var thread: HandlerThread? = null
    private var gameForeground = false
    private var processing = false
    private var lastAnalysisAt = 0L
    private var receiverRegistered = false

    private val gameReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            gameForeground = intent?.getBooleanExtra(MCGGAccessibilityService.EXTRA_IS_GAME, false) == true
            sendStatus(if (gameForeground) "MCGG aktif • analisis lokal berjalan" else "Menunggu MCGG di depan")
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(CHANNEL, "Analisis layar MCGG", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action != ACTION_START) return START_NOT_STICKY

        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL)
                .setContentTitle("MCGG FAST HAND • AI LOKAL")
                .setContentText("Analisis frame di RAM saat MCGG aktif")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build()
        } else Notification.Builder(this).setContentTitle("MCGG FAST HAND • AI LOKAL")
            .setSmallIcon(android.R.drawable.ic_menu_view).build()

        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else startForeground(NOTIFICATION_ID, notification)

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val data = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent
        if (resultCode != Activity.RESULT_OK || data == null) {
            sendStatus("Izin screen capture tidak valid")
            stopSelf()
            return START_NOT_STICKY
        }
        startCapture(resultCode, data)
        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        try {
            val filter = IntentFilter(MCGGAccessibilityService.ACTION_GAME_FOREGROUND)
            if (Build.VERSION.SDK_INT >= 33) registerReceiver(gameReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
            else @Suppress("DEPRECATION") registerReceiver(gameReceiver, filter)
            receiverRegistered = true
            // A broadcast may have happened before this service registered its receiver.
            gameForeground = MCGGAccessibilityService.isGameForeground

            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi
            thread = HandlerThread("mcgg-ai-frame-pipeline").also { it.start() }
            val handler = Handler(thread!!.looper)
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3)
            reader?.setOnImageAvailableListener({ imageReader ->
                val image = imageReader.acquireLatestImage() ?: return@setOnImageAvailableListener
                val now = SystemClock.elapsedRealtime()
                if (!gameForeground || processing || now - lastAnalysisAt < ANALYSIS_INTERVAL_MS) {
                    image.close()
                    return@setOnImageAvailableListener
                }
                processing = true
                lastAnalysisAt = now
                var bitmap: Bitmap? = null
                try {
                    bitmap = imageToBitmap(image)
                } catch (error: Exception) {
                    sendStatus("Frame gagal diproses: ${error.localizedMessage}")
                } finally {
                    image.close()
                }
                val frame = bitmap
                if (frame == null) {
                    processing = false
                    return@setOnImageAvailableListener
                }
                HeroOcrAnalyzer.analyzeBitmap(frame, HeroDatabase.names,
                    onSuccess = { analysis ->
                        val lines = analysis.matches.take(8).map {
                            "${it.heroName}|${(it.similarity * 100).toInt()}|${it.estimatedZone}"
                        }
                        val summary = if (lines.isEmpty()) "Tidak ada nama hero terbaca • menunggu frame berikutnya"
                            else lines.joinToString("\n")
                        sendBroadcast(Intent(ACTION_ANALYSIS).setPackage(packageName)
                            .putExtra(EXTRA_ANALYSIS, summary)
                            .putExtra(EXTRA_GAME_ACTIVE, true))
                        frame.recycle()
                        processing = false
                    },
                    onFailure = { error ->
                        sendStatus("OCR lokal gagal: ${error.localizedMessage}")
                        frame.recycle()
                        processing = false
                    }
                )
            }, handler)

            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, data)
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            }, handler)
            display = projection?.createVirtualDisplay(
                "MCGGFastHandLive", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader?.surface, null, handler
            )
            sendStatus(if (gameForeground) "Screen capture aktif • MCGG terdeteksi" else "Screen capture aktif • menunggu MCGG di depan")
        } catch (error: Exception) {
            sendStatus("Gagal memulai analisis: ${error.localizedMessage}")
            stopSelf()
        }
    }

    private fun sendStatus(message: String) {
        sendBroadcast(Intent(ACTION_ANALYSIS).setPackage(packageName)
            .putExtra(EXTRA_ANALYSIS, message)
            .putExtra(EXTRA_GAME_ACTIVE, gameForeground))
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowPadding = plane.rowStride - pixelStride * image.width
        val padded = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride, image.height, Bitmap.Config.ARGB_8888
        )
        padded.copyPixelsFromBuffer(buffer)
        val cropped = Bitmap.createBitmap(padded, 0, 0, image.width, image.height)
        padded.recycle()
        return cropped
    }

    private fun releaseCapture() {
        try { display?.release() } catch (_: Exception) {}
        display = null
        try { reader?.close() } catch (_: Exception) {}
        reader = null
        try { projection?.stop() } catch (_: Exception) {}
        projection = null
        thread?.quitSafely()
        thread = null
    }

    override fun onDestroy() {
        if (receiverRegistered) {
            try { unregisterReceiver(gameReceiver) } catch (_: Exception) {}
            receiverRegistered = false
        }
        releaseCapture()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

/** Local roster used by the live recognition pipeline. */
object HeroDatabase {
    val names = listOf(
        "Cici", "Dyrroth", "Eudora", "Minotaur", "Phoveus", "Uranus", "Vale", "Yi Sun-shin",
        "Angela", "Balmond", "Belerick", "Granger", "Harley", "Hylos", "Luo Yi", "Martis",
        "Miya", "Natalia", "Zhuxin", "Aldous", "Clint", "Edith", "Franco", "Hilda", "Kadita",
        "Karina", "Mathilda", "Paquito", "Saber", "Tigreal", "Wanwan", "Atlas", "Gord",
        "Gusion", "Irithel", "Karrie", "Lolita", "Lunox", "Sora", "Suyou", "Vexana", "Yin",
        "Zilong", "Alice", "Badang", "Baxia", "Fanny", "Lancelot", "Melissa", "Nana",
        "Odette", "Thamuz", "Alpha"
    )
}
