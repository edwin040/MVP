package com.mcgg.fasthand

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.squareup.picasso.Picasso

data class HeroItem(val name: String, val image: String)

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("mcgg_config", MODE_PRIVATE) }
    private val heroes = listOf(
        "Akai","Angela","Alice","Alpha","Atlas","Aurora","Badang","Bane","Belerick","Beatrix",
        "Chou","Claude","Cici","Dyrroth","Edith","Eudora","Fanny","Faramis","Franco","Freya",
        "Gatotkaca","Gord","Granger","Grock","Guinevere","Harith","Harley","Hayabusa","Irithel",
        "Johnson","Kadita","Karina","Karrie","Layla","Leomord","Ling","Lunox","Luo Yi","Martis",
        "Mathilda","Melissa","Minotaur","Miya","Moskov","Nana","Natalia","Odette","Paquito",
        "Phoveus","Roger","Saber","Sora","Thamuz","Tigreal","Vale","Vexana","Wanwan","X.Borg",
        "Yin","Zhuxin","Zilong"
    ).map { HeroItem(it, "https://raw.githubusercontent.com/Ceplin03/database-mlbb.Mobile-Legends-Bang-Bang/master/images-hero/${it.lowercase().replace(" ", "").replace(".", "").replace("luo yi", "luo_yi")}.png") }

    private val selected = linkedSetOf<String>()
    private lateinit var grid: GridLayout
    private lateinit var search: EditText
    private lateinit var selectedLabel: TextView
    private lateinit var commander: Spinner
    private lateinit var levelNine: CheckBox

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        selected.addAll(prefs.getStringSet("selected_heroes", emptySet()) ?: emptySet())
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 20, 18, 20)
            setBackgroundColor(Color.rgb(12, 20, 30))
        }
        fun label(value: String, size: Float = 14f) = TextView(this).apply {
            text = value; textSize = size; setTextColor(Color.WHITE)
            setPadding(0, 9, 0, 7)
        }
        root.addView(label("MCGG FAST HAND", 25f))
        root.addView(label("Pilih target dari portrait hero. Ketuk kartu untuk LOCK / batal LOCK."))
        search = EditText(this).apply {
            hint = "Cari hero..."; setSingleLine(true)
            setTextColor(Color.WHITE); setHintTextColor(0xFF9AA7B2.toInt())
            setPadding(14, 8, 14, 8)
            setBackgroundColor(0xFF1B2B3B.toInt())
        }
        root.addView(search, LinearLayout.LayoutParams(-1, 52))
        selectedLabel = label("")
        root.addView(selectedLabel)
        val gridScroll = ScrollView(this)
        grid = GridLayout(this).apply { columnCount = 3; useDefaultMargins = true }
        gridScroll.addView(grid)
        root.addView(gridScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(label("COMMANDER"))
        commander = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Pilih Commander", "Guinevere", "Lainnya"))
            setSelection(prefs.getInt("commander", 0))
        }
        root.addView(commander)
        levelNine = CheckBox(this).apply {
            text = "Saya sudah mencapai level 9 (konfirmasi manual)"
            setTextColor(Color.WHITE); isChecked = prefs.getBoolean("level9", false)
        }
        root.addView(levelNine)
        val save = Button(this).apply { text = "SIMPAN PILIHAN HERO" }
        root.addView(save)
        save.setOnClickListener {
            prefs.edit().putStringSet("selected_heroes", HashSet(selected))
                .putInt("commander", commander.selectedItemPosition)
                .putBoolean("level9", levelNine.isChecked).apply()
            Toast.makeText(this, "Pilihan tersimpan: ${selected.size} hero", Toast.LENGTH_SHORT).show()
        }
        val launch = Button(this).apply { text = "JALANKAN FLOATING PANEL" }
        root.addView(launch)
        launch.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
                Toast.makeText(this, "Panel mengambang dimulai", Toast.LENGTH_SHORT).show()
            }
        }
        root.addView(label("Catatan: pemilihan hero dan floating panel sudah tersedia. Pembacaan layar dan auto-tap masih tahap pengembangan; tombol ON belum menjalankan aksi dalam game.", 12f))
        setContentView(root)
        renderHeroes(heroes)
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                renderHeroes(heroes.filter { it.name.contains(s.toString(), true) })
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun renderHeroes(items: List<HeroItem>) {
        grid.removeAllViews()
        items.forEach { hero ->
            val isSelected = selected.contains(hero.name)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(5, 5, 5, 7)
                setBackgroundColor(if (isSelected) 0xFF155E59.toInt() else 0xFF1B2B3B.toInt())
            }
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = hero.name
            }
            Picasso.get().load(hero.image).fit().centerCrop()
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image).into(image)
            card.addView(image, LinearLayout.LayoutParams(dp(88), dp(88)))
            val name = TextView(this).apply {
                text = if (isSelected) "✓ ${hero.name}" else hero.name
                setTextColor(Color.WHITE); textSize = 12f; gravity = Gravity.CENTER
                setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
                maxLines = 1
            }
            card.addView(name, LinearLayout.LayoutParams(-1, dp(30)))
            card.setOnClickListener {
                if (!selected.add(hero.name)) selected.remove(hero.name)
                renderHeroes(heroes.filter { it.name.contains(search.text.toString(), true) })
                updateSelectedLabel()
            }
            val lp = GridLayout.LayoutParams().apply {
                width = 0; height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(3), dp(3), dp(3), dp(3))
            }
            grid.addView(card, lp)
        }
        updateSelectedLabel()
    }

    private fun updateSelectedLabel() {
        selectedLabel.text = "HERO TERKUNCI: ${selected.size} • " +
            if (selected.isEmpty()) "Belum ada pilihan" else selected.joinToString(", ")
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
