package com.mcgg.fasthand

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("mcgg_config", MODE_PRIVATE) }
    private lateinit var heroes: EditText
    private lateinit var commander: Spinner
    private lateinit var levelNine: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 28, 24, 24)
            setBackgroundColor(0xFF101820.toInt())
        }
        fun label(text: String, size: Float = 15f) = TextView(this).apply {
            this.text = text; textSize = size; setTextColor(0xFFF3F6F8.toInt())
            setPadding(0, 12, 0, 8)
        }
        root.addView(label("MCGG FAST HAND", 25f))
        root.addView(label("Pilih target hero sebelum pertandingan. Pisahkan nama dengan koma."))
        heroes = EditText(this).apply {
            hint = "Contoh: hero A, hero B"; setTextColor(0xFFFFFFFF.toInt())
            setHintTextColor(0xFF9AA7B2.toInt()); setText(prefs.getString("heroes", ""))
            setSingleLine(false); minLines = 2
        }
        root.addView(heroes, LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(label("Commander"))
        commander = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Pilih Commander", "Guinevere", "Commander lain"))
            setSelection(prefs.getInt("commander", 0))
        }
        root.addView(commander)
        levelNine = CheckBox(this).apply {
            text = "Saya sudah mencapai level 9 (konfirmasi manual)"
            setTextColor(0xFFF3F6F8.toInt()); isChecked = prefs.getBoolean("level9", false)
        }
        root.addView(levelNine)
        val save = Button(this).apply { text = "SIMPAN PENGATURAN" }
        root.addView(save)
        save.setOnClickListener {
            prefs.edit().putString("heroes", heroes.text.toString())
                .putInt("commander", commander.selectedItemPosition)
                .putBoolean("level9", levelNine.isChecked).apply()
            Toast.makeText(this, "Pengaturan tersimpan", Toast.LENGTH_SHORT).show()
        }
        val start = Button(this).apply { text = "TAMPILKAN FLOATING PANEL" }
        root.addView(start)
        start.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")))
            } else {
                startService(Intent(this, OverlayService::class.java))
                Toast.makeText(this, "Floating panel dijalankan", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(label("Catatan keamanan", 18f))
        root.addView(label("Versi prototipe ini hanya menyediakan panel kontrol dan konfigurasi. Belum melakukan tap, reroll, beli, atau jual otomatis. Jangan gunakan automation pihak ketiga jika bertentangan dengan aturan game."))
        setContentView(ScrollView(this).apply { addView(root) })
    }
}
