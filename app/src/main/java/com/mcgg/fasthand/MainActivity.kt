package com.mcgg.fasthand

import android.content.Intent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import android.media.projection.MediaProjectionManager
import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.RectF
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.activity.result.contract.ActivityResultContracts
import com.squareup.picasso.Picasso

data class HeroItem(val name: String, val gold: Int) {
    val image: String get() = "https://loadoutgg.com/characters/magic-chess-gogo/${name.lowercase().replace(" ", "-").replace("'", "").replace(".", "-")}/1.png"
}

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("mcgg_config", MODE_PRIVATE) }
    // Current MCGG roster (Season 7 database), not the full Mobile Legends roster.
    private val heroes = listOf(
        HeroItem("Cici",1), HeroItem("Dyrroth",1), HeroItem("Eudora",1), HeroItem("Minotaur",1),
        HeroItem("Phoveus",1), HeroItem("Uranus",1), HeroItem("Vale",1), HeroItem("Yi Sun-shin",1),
        HeroItem("Angela",2), HeroItem("Balmond",2), HeroItem("Belerick",2), HeroItem("Granger",2),
        HeroItem("Harley",2), HeroItem("Hylos",2), HeroItem("Luo Yi",2), HeroItem("Martis",2),
        HeroItem("Miya",2), HeroItem("Natalia",2), HeroItem("Zhuxin",2),
        HeroItem("Aldous",3), HeroItem("Clint",3), HeroItem("Edith",3), HeroItem("Franco",3),
        HeroItem("Hilda",3), HeroItem("Kadita",3), HeroItem("Karina",3), HeroItem("Mathilda",3),
        HeroItem("Paquito",3), HeroItem("Saber",3), HeroItem("Tigreal",3), HeroItem("Wanwan",3),
        HeroItem("Atlas",4), HeroItem("Gord",4), HeroItem("Gusion",4), HeroItem("Irithel",4),
        HeroItem("Karrie",4), HeroItem("Lolita",4), HeroItem("Lunox",4), HeroItem("Sora",4),
        HeroItem("Suyou",4), HeroItem("Vexana",4), HeroItem("Yin",4), HeroItem("Zilong",4),
        HeroItem("Alice",5), HeroItem("Badang",5), HeroItem("Baxia",5), HeroItem("Fanny",5),
        HeroItem("Lancelot",5), HeroItem("Melissa",5), HeroItem("Nana",5), HeroItem("Odette",5),
        HeroItem("Thamuz",5), HeroItem("Alpha",7)
    )
    private val selected = linkedSetOf<String>()
    private lateinit var grid: GridLayout
    private lateinit var search: EditText
    private lateinit var selectedLabel: TextView
    private lateinit var commander: Spinner
    private lateinit var levelNine: CheckBox
    private lateinit var ocrOutput: TextView
    private lateinit var visualOutput: TextView
    private lateinit var cropFields: List<EditText>
    private lateinit var visualHero: Spinner
    private var visualAction: String = "enroll"
    private var captureReceiver: BroadcastReceiver? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val capturePermission = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                val serviceIntent = Intent(this, ScreenCaptureService::class.java)
                    .setAction(ScreenCaptureService.ACTION_START)
                    .putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                    .putExtra(ScreenCaptureService.EXTRA_RESULT_DATA, result.data)
                ContextCompat.startForegroundService(this, serviceIntent)
                ocrOutput.text = "Izin diberikan. AI lokal aktif; buka MCGG agar analisis dimulai."
            } else {
                ocrOutput.text = "Izin tangkapan layar dibatalkan. Tidak ada layar yang direkam."
            }
        }
        captureReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val message = intent?.getStringExtra(ScreenCaptureService.EXTRA_ANALYSIS) ?: return
                ocrOutput.text = if (intent.getBooleanExtra(ScreenCaptureService.EXTRA_GAME_ACTIVE, false))
                    "AI LOKAL • MCGG AKTIF\\n$message"
                else message
            }
        }
        val captureFilter = IntentFilter(ScreenCaptureService.ACTION_ANALYSIS)
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            registerReceiver(captureReceiver, captureFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            registerReceiver(captureReceiver, captureFilter)
        }
        val screenshotPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                ocrOutput.text = "OCR sedang membaca screenshot..."
                HeroOcrAnalyzer.analyze(
                    this, uri, heroes.map { it.name },
                    onSuccess = { analysis ->
                        val lines = analysis.matches.take(8).map { match ->
                            val percent = (match.similarity * 100).toInt()
                            val position = match.bounds?.let { " • posisi ${it.centerX()},${it.centerY()}" } ?: ""
                            val zone = match.estimatedZone
                            val normalized = if (match.normalizedCenterX >= 0f && match.normalizedCenterY >= 0f)
                                " • relatif ${"%.2f".format(match.normalizedCenterX)},${"%.2f".format(match.normalizedCenterY)}" else ""
                            "• ${match.heroName} — ${percent}% cocok • ${zone}${position}${normalized} (teks: ${match.recognizedText})"
                        }
                        ocrOutput.text = if (lines.isEmpty()) {
                            "Belum ada nama hero yang cocok. Pastikan screenshot tajam dan nama hero terlihat.\nTeks OCR: ${analysis.fullText.take(180)}"
                        } else {
                            "HASIL HERO & ZONA (zona hanya perkiraan):\n" + lines.joinToString("\n")
                        }
                    },
                    onFailure = { error ->
                        ocrOutput.text = "OCR gagal: ${error.localizedMessage ?: "gambar tidak dapat dibaca"}"
                    }
                )
            }
        }
        val visualPicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                visualOutput.text = "Memproses gambar referensi..."
                Thread {
                    try {
                        val crop = readVisualCrop()
                        val heroName = visualHero.selectedItem?.toString().orEmpty()
                        if (visualAction == "enroll") {
                            HeroVisualMatcher.enroll(this, uri, heroName, crop)
                            runOnUiThread {
                                visualOutput.text = "Referensi $heroName tersimpan. Total template: ${HeroVisualMatcher.templateCount(this)}. Gunakan crop yang sama saat pengujian."
                            }
                        } else {
                            val matches = HeroVisualMatcher.match(this, uri, crop)
                            runOnUiThread {
                                visualOutput.text = if (matches.isEmpty()) {
                                    "Belum ada template. Simpan portrait hero terlebih dahulu."
                                } else {
                                    val rows = matches.mapIndexed { index, match ->
                                        val score = (match.score * 100).toInt()
                                        "${index + 1}. ${match.heroName} — ${score}%"
                                    }
                                    val top = matches.first()
                                    val status = if (top.score >= 0.82f) "KANDIDAT KUAT" else "UNKNOWN • BELUM YAKIN"
                                    "$status\n" + rows.joinToString("\n") +
                                        "\nCatatan: skor kemiripan visual, bukan probabilitas identitas."
                                }
                            }
                        }
                    } catch (error: Exception) {
                        runOnUiThread {
                            visualOutput.text = "Pengenalan visual gagal: ${error.localizedMessage ?: "gambar tidak valid"}"
                        }
                    }
                }.start()
            }
        }
        selected.addAll(prefs.getStringSet("selected_heroes", emptySet()) ?: emptySet())
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(16))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR,
                intArrayOf(0xFF0A1422.toInt(), 0xFF14283A.toInt())).apply { cornerRadius = dp(22).toFloat() }
        }
        fun text(value: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false) =
            TextView(this).apply {
                text = value; textSize = size; setTextColor(color)
                if (bold) setTypeface(null, Typeface.BOLD)
            }
        val title = text("MCGG FAST HAND", 25f, 0xFFECF7FF.toInt(), true)
        root.addView(title)
        val subtitle = text("Pilih hero target dari roster Magic Chess: Go Go", 13f, 0xFFA9C0D2.toInt())
        root.addView(subtitle)
        val count = text("SEASON 7  •  53 HERO DATABASE", 11f, 0xFF65D6B0.toInt(), true)
        count.setPadding(0, dp(10), 0, dp(10)); root.addView(count)
        search = EditText(this).apply {
            hint = "⌕  Cari hero MCGG"; setSingleLine(true)
            textSize = 14f; setTextColor(Color.WHITE); setHintTextColor(0xFF91A6B7.toInt())
            setPadding(dp(14), 0, dp(14), 0)
            background = rounded(0xFF1B3043.toInt(), 14)
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(48)))
        selectedLabel = text("", 12f, 0xFF77E3C1.toInt(), true)
        selectedLabel.setPadding(dp(2), dp(10), dp(2), dp(10)); root.addView(selectedLabel)
        val scroll = ScrollView(this).apply { isFillViewport = false }
        grid = GridLayout(this).apply { columnCount = 3; useDefaultMargins = false }
        scroll.addView(grid)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(text("COMMANDER", 11f, 0xFF91A6B7.toInt(), true))
        commander = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item,
                listOf("Pilih Commander", "Guinevere", "Nana", "Minotaur", "Brody", "Franco", "Lylia", "Lukas", "Commander lain"))
            setSelection(prefs.getInt("commander", 0))
        }
        root.addView(commander)
        levelNine = CheckBox(this).apply {
            text = "Konfirmasi: Commander level 9"; textSize = 12f
            setTextColor(0xFFD9E8F3.toInt()); isChecked = prefs.getBoolean("level9", false)
        }
        root.addView(levelNine)
        val save = Button(this).apply {
            text = "SIMPAN SETUP"; isAllCaps = false
            background = rounded(0xFF168D78.toInt(), 14); setTextColor(Color.WHITE)
        }
        root.addView(save, LinearLayout.LayoutParams(-1, dp(48)))
        val ocrButton = Button(this).apply {
            text = "UJI OCR & KENALI HERO DARI SCREENSHOT"; isAllCaps = false
            background = rounded(0xFF315B78.toInt(), 14); setTextColor(Color.WHITE)
        }
        val ocrLp = LinearLayout.LayoutParams(-1, dp(46)); ocrLp.topMargin = dp(7)
        root.addView(ocrButton, ocrLp)
        val liveCaptureButton = Button(this).apply {
            text = "AKTIFKAN AI LOKAL • IZINKAN BACA LAYAR"; isAllCaps = false
            background = rounded(0xFF176A60.toInt(), 14); setTextColor(Color.WHITE)
        }
        val liveLp = LinearLayout.LayoutParams(-1, dp(46)); liveLp.topMargin = dp(5)
        root.addView(liveCaptureButton, liveLp)
        liveCaptureButton.setOnClickListener {
            val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            capturePermission.launch(manager.createScreenCaptureIntent())
        }
        val stopCaptureButton = Button(this).apply {
            text = "HENTIKAN AI SCREEN ANALYSIS"; isAllCaps = false
            background = rounded(0xFF75434A.toInt(), 12); setTextColor(Color.WHITE)
        }
        val stopLp = LinearLayout.LayoutParams(-1, dp(42)); stopLp.topMargin = dp(4)
        root.addView(stopCaptureButton, stopLp)
        stopCaptureButton.setOnClickListener {
            startService(Intent(this, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP))
            ocrOutput.text = "AI lokal dihentikan."
        }
        ocrOutput = text("AI lokal siap. Izinkan screen capture, aktifkan Accessibility, lalu buka MCGG. Frame dianalisis di RAM tanpa menyimpan screenshot.", 10.5f, 0xFFB8CAD8.toInt())
        ocrOutput.setPadding(dp(4), dp(8), dp(4), dp(8))
        root.addView(ocrOutput)
        val visualTitle = text("PENGENALAN HERO DARI GAMBAR", 13f, 0xFF78DCC0.toInt(), true)
        visualTitle.setPadding(0, dp(12), 0, dp(5))
        root.addView(visualTitle)
        val visualHint = text(
            "Pilih hero, lalu masukkan area portrait sebagai koordinat relatif X, Y, Lebar, Tinggi (0–1). Contoh: 0.16, 0.23, 0.07, 0.13. Simpan referensi dari katalog yang identitasnya kamu ketahui.",
            10.5f, 0xFFB8CAD8.toInt()
        )
        root.addView(visualHint)
        visualHero = Spinner(this).apply {
            adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, heroes.map { it.name })
        }
        root.addView(visualHero, LinearLayout.LayoutParams(-1, dp(42)))
        val cropRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val labels = listOf("X", "Y", "W", "H")
        val defaults = listOf("0.16", "0.23", "0.07", "0.13")
        cropFields = labels.indices.map { index ->
            EditText(this).apply {
                hint = labels[index]
                setSingleLine(true)
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                    android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                setText(defaults[index])
                textSize = 12f
                setTextColor(Color.WHITE)
                setHintTextColor(0xFF91A6B7.toInt())
                background = rounded(0xFF1B3043.toInt(), 9)
                setPadding(dp(7), 0, dp(7), 0)
            }.also { field ->
                val lp = LinearLayout.LayoutParams(0, dp(42), 1f)
                lp.setMargins(dp(2), dp(4), dp(2), dp(4))
                cropRow.addView(field, lp)
            }
        }
        root.addView(cropRow)
        val enrollButton = Button(this).apply {
            text = "SIMPAN PORTRAIT SEBAGAI REFERENSI"; isAllCaps = false
            background = rounded(0xFF315B78.toInt(), 12); setTextColor(Color.WHITE)
        }
        root.addView(enrollButton, LinearLayout.LayoutParams(-1, dp(44)))
        val matchButton = Button(this).apply {
            text = "COCOKKAN GAMBAR DENGAN DATABASE"; isAllCaps = false
            background = rounded(0xFF176A60.toInt(), 12); setTextColor(Color.WHITE)
        }
        val matchLp = LinearLayout.LayoutParams(-1, dp(44)); matchLp.topMargin = dp(5)
        root.addView(matchButton, matchLp)
        visualOutput = text("Template tersimpan: 0. Hasil pencocokan hanya kandidat dan belum mengendalikan Auto Roll.", 10.5f, 0xFFB8CAD8.toInt())
        visualOutput.setPadding(dp(4), dp(8), dp(4), dp(8))
        root.addView(visualOutput)
        enrollButton.setOnClickListener {
            visualAction = "enroll"
            visualPicker.launch("image/*")
        }
        matchButton.setOnClickListener {
            visualAction = "match"
            visualPicker.launch("image/*")
        }
        ocrButton.setOnClickListener { screenshotPicker.launch("image/*") }
        save.setOnClickListener {
            prefs.edit().putStringSet("selected_heroes", HashSet(selected))
                .putInt("commander", commander.selectedItemPosition)
                .putBoolean("level9", levelNine.isChecked).apply()
            Toast.makeText(this, "Setup tersimpan • ${selected.size} hero", Toast.LENGTH_SHORT).show()
        }
        val launch = Button(this).apply {
            text = "BUKA FLOATING CONTROL"; isAllCaps = false
            background = rounded(0xFF263E55.toInt(), 14); setTextColor(Color.WHITE)
        }
        val launchLp = LinearLayout.LayoutParams(-1, dp(48)); launchLp.topMargin = dp(8)
        root.addView(launch, launchLp)
        val accessibility = Button(this).apply {
            text = "AKTIFKAN IZIN DETEKSI APLIKASI"; isAllCaps = false
            background = rounded(0xFF34495E.toInt(), 14); setTextColor(Color.WHITE)
        }
        val accessLp = LinearLayout.LayoutParams(-1, dp(44)); accessLp.topMargin = dp(7)
        root.addView(accessibility, accessLp)
        if (!BuildConfig.ENABLE_FULL_FEATURES) {
            launch.isEnabled = false
            launch.alpha = 0.45f
            launch.text = "FLOATING CONTROL • EDISI FULL"
            accessibility.isEnabled = false
            accessibility.alpha = 0.45f
            accessibility.text = "AKSESIBILITAS TIDAK DIPAKAI DI LITE"
        }
        accessibility.setOnClickListener {
            if (BuildConfig.ENABLE_FULL_FEATURES) startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        launch.setOnClickListener {
            if (!BuildConfig.ENABLE_FULL_FEATURES) return@setOnClickListener
            prefs.edit().putStringSet("selected_heroes", HashSet(selected))
                .putInt("commander", commander.selectedItemPosition)
                .putBoolean("level9", levelNine.isChecked).apply()
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
            }
        }
        val noteText = if (BuildConfig.ENABLE_FULL_FEATURES)
            "Edisi Full memakai Accessibility dan floating overlay untuk deteksi aplikasi depan. Pengenalan papan dan aksi game otomatis belum tersedia."
        else
            "Edisi Lite tidak meminta izin Accessibility atau floating overlay. Setup hero dapat disimpan; kontrol mengambang tidak tersedia."
        val note = text(noteText, 11f, 0xFF8FA5B6.toInt())
        note.setPadding(dp(2), dp(10), dp(2), 0); root.addView(note)
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

    override fun onDestroy() {
        captureReceiver?.let {
            try { unregisterReceiver(it) } catch (_: Exception) {}
        }
        captureReceiver = null
        super.onDestroy()
    }

    private fun readVisualCrop(): RectF {
        val values = cropFields.map { it.text.toString().toFloatOrNull()
            ?: throw IllegalArgumentException("Isi X, Y, W, H dengan angka 0–1.") }
        val (x, y, width, height) = values
        if (x < 0f || y < 0f || width <= 0f || height <= 0f ||
            x + width > 1f || y + height > 1f) {
            throw IllegalArgumentException("Area crop harus berada dalam batas 0–1.")
        }
        return RectF(x, y, x + width, y + height)
    }

    private fun renderHeroes(items: List<HeroItem>) {
        grid.removeAllViews()
        items.forEach { hero ->
            val chosen = selected.contains(hero.name)
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER
                setPadding(dp(5), dp(5), dp(5), dp(6))
                background = rounded(if (chosen) 0xFF176A60.toInt() else 0xFF1A2D40.toInt(), 13)
            }
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                contentDescription = hero.name
            }
            Picasso.get().load(hero.image).fit().centerCrop()
                .placeholder(android.R.drawable.ic_menu_gallery)
                .error(android.R.drawable.ic_menu_report_image).into(image)
            card.addView(image, LinearLayout.LayoutParams(dp(82), dp(82)))
            val name = TextView(this).apply {
                text = (if (chosen) "✓ " else "") + hero.name
                setTextColor(Color.WHITE); textSize = 11f; gravity = Gravity.CENTER
                setTypeface(null, if (chosen) Typeface.BOLD else Typeface.NORMAL); maxLines = 1
            }
            card.addView(name, LinearLayout.LayoutParams(-1, dp(25)))
            val gold = TextView(this).apply {
                text = if (hero.gold == 7) "7 GOLD" else "${hero.gold} GOLD"
                textSize = 9f; gravity = Gravity.CENTER; setTextColor(0xFFFFD58A.toInt())
            }
            card.addView(gold, LinearLayout.LayoutParams(-1, dp(16)))
            card.setOnClickListener {
                if (!selected.add(hero.name)) selected.remove(hero.name)
                renderHeroes(heroes.filter { it.name.contains(search.text.toString(), true) })
            }
            val lp = GridLayout.LayoutParams().apply {
                width = 0; height = ViewGroup.LayoutParams.WRAP_CONTENT
                columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                setMargins(dp(3), dp(3), dp(3), dp(5))
            }
            grid.addView(card, lp)
        }
        selectedLabel.text = "LOCKED: ${selected.size} HERO"
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
}
