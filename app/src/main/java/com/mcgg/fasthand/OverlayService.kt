package com.mcgg.fasthand

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter

class OverlayService : Service() {
    companion object { const val ACTION_CLOSE = "com.mcgg.fasthand.CLOSE_FLOATING" }
    private lateinit var wm: WindowManager
    private var panel: View? = null
    private var enabled = false
    private var gameForeground = false
    private val gameReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            gameForeground = intent?.getBooleanExtra(MCGGAccessibilityService.EXTRA_IS_GAME, false) == true
            if (!gameForeground) enabled = false
            refreshStatus()
        }
    }
    private var status: TextView? = null
    private var toggle: Button? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val channel = "mcgg_overlay"
        if (Build.VERSION.SDK_INT >= 26) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(channel, "MCGG Fast Hand", NotificationManager.IMPORTANCE_LOW))
        }
        val closeIntent = PendingIntent.getService(this, 42,
            Intent(this, OverlayService::class.java).setAction(ACTION_CLOSE),
            PendingIntent.FLAG_UPDATE_CURRENT or if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        val notification = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, channel).setContentTitle("MCGG FAST HAND")
                .setContentText("Floating control aktif")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Tutup floating", closeIntent).build()
        else Notification.Builder(this).setContentTitle("MCGG FAST HAND")
            .setSmallIcon(android.R.drawable.ic_media_play).addAction(android.R.drawable.ic_menu_close_clear_cancel, "Tutup floating", closeIntent).build()
        startForeground(41, notification)
        showPanel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CLOSE) {
            stopFloating()
            return START_NOT_STICKY
        }
        return START_STICKY
    }

    private fun params() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.END; x = 18; y = 160 }

    private fun showPanel() {
        if (panel != null) return
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(12, 10, 12, 10)
            background = GradientDrawable().apply {
                setColor(0xF018293B.toInt()); cornerRadius = 20f
                setStroke(1, 0xFF3A5A70.toInt())
            }
        }
        val headerRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val header = TextView(this).apply {
            text = "✥  MCGG CONTROL"; textSize = 13f; setTextColor(Color.WHITE)
            setPadding(5, 5, 16, 10)
        }
        val close = TextView(this).apply {
            text = "×"; textSize = 23f; gravity = Gravity.CENTER
            setTextColor(0xFFFF8C8C.toInt()); setPadding(10, 0, 5, 7)
            setOnClickListener { stopFloating() }
        }
        headerRow.addView(header, LinearLayout.LayoutParams(0, -2, 1f))
        headerRow.addView(close, LinearLayout.LayoutParams(38, 38))
        box.addView(headerRow)
        status = TextView(this).apply {
            text = "● STANDBY  •  OFF"; setTextColor(0xFF65D6B0.toInt()); textSize = 11f
            setPadding(5, 2, 5, 10)
        }
        box.addView(status)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        toggle = Button(this).apply { text = "ON"; isAllCaps = false }
        val stop = Button(this).apply { text = "STOP"; isAllCaps = false }
        val mini = Button(this).apply { text = "MIN"; isAllCaps = false }
        row.addView(toggle); row.addView(stop); row.addView(mini); box.addView(row)
        toggle?.setOnClickListener {
            enabled = !enabled
            status?.text = if (enabled) "● ON • MODE UJI" else "● STANDBY • OFF"
            toggle?.text = if (enabled) "OFF" else "ON"
        }
        stop.setOnClickListener {
            enabled = false; refreshStatus(); status?.text = "● STOPPED"
        }
        mini.setOnClickListener { minimize() }
        header.setOnTouchListener(dragListener())
        panel = box
        wm.addView(box, params())
        refreshStatus()
    }

    private fun refreshStatus() {
        status?.text = when {
            enabled && gameForeground -> "● MCGG TERDETEKSI • AUTO ON"
            !gameForeground -> "● TERKUNCI • BUKA MCGG"
            else -> "● MCGG AKTIF • AUTO OFF"
        }
        toggle?.text = if (enabled) "OFF" else "ON"
        toggle?.isEnabled = gameForeground
        toggle?.alpha = if (gameForeground) 1f else 0.45f
    }

    private fun dragListener() = object : View.OnTouchListener {
        var sx=0f; var sy=0f; var ox=0; var oy=0
        override fun onTouch(v: View, e: MotionEvent): Boolean {
            val lp = panel?.layoutParams as? WindowManager.LayoutParams ?: return false
            when (e.action) {
                MotionEvent.ACTION_DOWN -> { sx=e.rawX; sy=e.rawY; ox=lp.x; oy=lp.y; return true }
                MotionEvent.ACTION_MOVE -> {
                    lp.x=ox-(e.rawX-sx).toInt(); lp.y=oy+(e.rawY-sy).toInt()
                    panel?.let { try { wm.updateViewLayout(it,lp) } catch (_: Exception) {} }; return true
                }
            }
            return false
        }
    }

    private fun minimize() {
        val old = panel ?: return
        try { wm.removeView(old) } catch (_: Exception) {}
        panel = null
        val bubble = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER
            background = GradientDrawable().apply { setColor(0xFF167D72.toInt()); cornerRadius = 28f }
        }
        val reopen = TextView(this).apply {
            text = "MC"; textSize = 14f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(17, 13, 12, 13)
            setOnClickListener { removeBubbleAndReopen() }
        }
        val close = TextView(this).apply {
            text = "×"; textSize = 19f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(4, 8, 12, 8); setOnClickListener { stopFloating() }
        }
        bubble.addView(reopen); bubble.addView(close)
        panel = bubble
        wm.addView(bubble, params())
    }

    private fun removeBubbleAndReopen() {
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel = null
        showPanel()
    }

    private fun stopFloating() {
        enabled = false
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel = null
        stopForeground(true)
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) { super.onTaskRemoved(rootIntent) }
    override fun onDestroy() {
        enabled = false
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
