package com.mcgg.fasthand

import android.app.*
import android.content.Intent
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
import android.util.DisplayMetrics
import android.view.WindowManager
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer

/**
 * User-consented one-shot screen capture. Captures the current display once, saves it privately,
 * broadcasts only the private content URI, then releases MediaProjection resources.
 */
class ScreenCaptureService : Service() {
    companion object {
        const val ACTION_START = "com.mcgg.fasthand.SCREEN_CAPTURE_START"
        const val ACTION_CAPTURED = "com.mcgg.fasthand.SCREEN_CAPTURED"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_RESULT_DATA = "result_data"
        const val EXTRA_IMAGE_URI = "image_uri"
        private const val CHANNEL = "mcgg_screen_capture"
        private const val NOTIFICATION_ID = 52
    }

    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var thread: HandlerThread? = null
    private var captured = false

    override fun onCreate() {
        super.onCreate()
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Izin baca layar MCGG", NotificationManager.IMPORTANCE_LOW)
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action != ACTION_START) return START_NOT_STICKY
        val notification = if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL)
                .setContentTitle("MCGG FAST HAND")
                .setContentText("Mengambil satu frame layar atas izin pengguna")
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .build()
        } else {
            Notification.Builder(this).setContentTitle("MCGG FAST HAND")
                .setSmallIcon(android.R.drawable.ic_menu_view).build()
        }
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        } else startForeground(NOTIFICATION_ID, notification)

        val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
        val data = if (Build.VERSION.SDK_INT >= 33)
            intent.getParcelableExtra(EXTRA_RESULT_DATA, Intent::class.java)
        else @Suppress("DEPRECATION") intent.getParcelableExtra(EXTRA_RESULT_DATA) as? Intent
        if (resultCode != Activity.RESULT_OK || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        startCapture(resultCode, data)
        return START_NOT_STICKY
    }

    private fun startCapture(resultCode: Int, data: Intent) {
        try {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (getSystemService(WINDOW_SERVICE) as WindowManager).defaultDisplay.getRealMetrics(metrics)
            val width = metrics.widthPixels
            val height = metrics.heightPixels
            val density = metrics.densityDpi
            thread = HandlerThread("mcgg-screen-capture").also { it.start() }
            val handler = Handler(thread!!.looper)
            reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(resultCode, data)
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { releaseCapture() }
            }, handler)
            display = projection?.createVirtualDisplay(
                "MCGGFastHandCapture", width, height, density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader?.surface, null, handler
            )
            reader?.setOnImageAvailableListener({ imageReader ->
                if (captured) return@setOnImageAvailableListener
                val image = imageReader.acquireLatestImage() ?: return@setOnImageAvailableListener
                captured = true
                try {
                    val bitmap = imageToBitmap(image)
                    val file = File(cacheDir, "mcgg_capture_${System.currentTimeMillis()}.jpg")
                    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                    bitmap.recycle()
                    sendBroadcast(Intent(ACTION_CAPTURED).setPackage(packageName)
                        .putExtra(EXTRA_IMAGE_URI, android.net.Uri.fromFile(file).toString()))
                } catch (error: Exception) {
                    sendBroadcast(Intent(ACTION_CAPTURED).setPackage(packageName)
                        .putExtra("error", error.localizedMessage ?: "Gagal menyimpan frame"))
                } finally {
                    image.close()
                    stopSelf()
                }
            }, handler)
        } catch (error: Exception) {
            sendBroadcast(Intent(ACTION_CAPTURED).setPackage(packageName)
                .putExtra("error", error.localizedMessage ?: "Gagal memulai screen capture"))
            stopSelf()
        }
    }

    private fun imageToBitmap(image: Image): Bitmap {
        val plane = image.planes[0]
        val buffer: ByteBuffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * image.width
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
        releaseCapture()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
