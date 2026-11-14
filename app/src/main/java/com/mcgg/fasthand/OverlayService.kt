package com.mcgg.fasthand

import android.app.*
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*

class OverlayService : Service() {
    private lateinit var wm: WindowManager
    private var panel: View? = null
    private var minimized = false
    private var enabled = false
    private var status: TextView? = null

    override fun onCreate() {
        super.onCreate()
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val channel = "mcgg_overlay"
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel(channel, "MCGG Fast Hand", NotificationManager.IMPORTANCE_LOW))
        }
        val notification = if (Build.VERSION.SDK_INT >= 26)
            Notification.Builder(this, channel).setContentTitle("MCGG FAST HAND")
                .setContentText("Floating control aktif").setSmallIcon(android.R.drawable.ic_media_play).build()
        else Notification.Builder(this).setContentTitle("MCGG FAST HAND")
            .setSmallIcon(android.R.drawable.ic_media_play).build()
        startForeground(41, notification)
        showPanel()
    }

    private fun params() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
        if (Build.VERSION.SDK_INT >= 26) WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY else WindowManager.LayoutParams.TYPE_PHONE,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.END; x = 24; y = 180 }

    private fun showPanel() {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(14, 12, 14, 12)
            setBackgroundColor(Color.rgb(20, 34, 45))
        }
        val header = TextView(this).apply {
            text = "☰  MCGG FAST HAND"; textSize = 14f; setTextColor(Color.WHITE)
            setPadding(6, 6, 6, 12)
        }
        box.addView(header)
        status = TextView(this).apply {
            text = "● STANDBY • OFF"; setTextColor(0xFF65D6B0.toInt()); textSize = 12f
            setPadding(6, 4, 6, 10)
        }
        box.addView(status)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val toggle = Button(this).apply { text = "ON" }
        val stop = Button(this).apply { text = "STOP" }
        val mini = Button(this).apply { text = "—" }
        row.addView(toggle); row.addView(stop); row.addView(mini); box.addView(row)
        toggle.setOnClickListener {
            enabled = !enabled
            status?.text = if (enabled) "● ON • PROTOTYPE" else "● STANDBY • OFF"
            toggle.text = if (enabled) "OFF" else "ON"
        }
        stop.setOnClickListener {
            enabled = false; toggle.text = "ON"; status?.text = "● EMERGENCY STOP"
        }
        mini.setOnClickListener { minimize() }
        header.setOnTouchListener(object : View.OnTouchListener {
            var sx = 0f; var sy = 0f; var ox = 0; var oy = 0
            override fun onTouch(v: View, e: MotionEvent): Boolean {
                val lp = panel?.layoutParams as? WindowManager.LayoutParams ?: return false
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> { sx=e.rawX; sy=e.rawY; ox=lp.x; oy=lp.y; return true }
                    MotionEvent.ACTION_MOVE -> { lp.x=ox-(e.rawX-sx).toInt(); lp.y=oy+(e.rawY-sy).toInt(); panel?.let { wm.updateViewLayout(it,lp) }; return true }
                }
                return false
            }
        })
        panel = box
        wm.addView(box, params())
    }

    private fun minimize() {
        val old = panel ?: return
        wm.removeView(old)
        minimized = true
        val bubble = TextView(this).apply {
            text = "MC"; textSize = 16f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
            setPadding(16, 16, 16, 16); setBackgroundColor(0xFF167D72.toInt())
            setOnClickListener { wm.removeView(this); minimized=false; showPanel() }
        }
        panel = bubble
        wm.addView(bubble, params())
    }

    override fun onDestroy() {
        enabled = false
        panel?.let { try { wm.removeView(it) } catch (_: Exception) {} }
        panel = null
        super.onDestroy()
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
