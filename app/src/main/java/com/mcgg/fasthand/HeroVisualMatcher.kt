package com.mcgg.fasthand

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import android.graphics.RectF
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

/**
 * On-device visual template matcher.
 *
 * Templates are manually labeled crops from verified MCGG screenshots. This is a first
 * recognition stage, not an automatic detector: the caller supplies the normalized crop
 * rectangle. Unknown/low-confidence results must never trigger game actions.
 */
object HeroVisualMatcher {
    data class Match(val heroName: String, val score: Float)

    private const val PREFS = "hero_visual_templates"
    private const val TEMPLATE_PREFIX = "template_"
    private const val SIZE = 32

    fun enroll(context: Context, imageUri: Uri, heroName: String, normalizedCrop: RectF) {
        require(heroName.isNotBlank()) { "Pilih nama hero terlebih dahulu." }
        val source = decode(context, imageUri)
        val crop = cropNormalized(source, normalizedCrop)
        val fileName = TEMPLATE_PREFIX + heroName.lowercase()
            .replace(Regex("[^a-z0-9]+"), "_").trim('_') + ".png"
        val destination = File(context.filesDir, fileName)
        FileOutputStream(destination).use { output ->
            check(crop.compress(Bitmap.CompressFormat.PNG, 100, output)) {
                "Gagal menyimpan gambar referensi."
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(fileName, heroName).apply()
    }

    fun match(
        context: Context,
        imageUri: Uri,
        normalizedCrop: RectF,
        limit: Int = 5
    ): List<Match> {
        val source = decode(context, imageUri)
        val candidate = cropNormalized(source, normalizedCrop)
        val candidateSignature = signature(candidate)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return prefs.all.entries.mapNotNull { entry ->
            val fileName = entry.key
            val heroName = entry.value as? String ?: return@mapNotNull null
            val file = File(context.filesDir, fileName)
            if (!file.isFile) return@mapNotNull null
            val template = BitmapFactory.decodeFile(file.absolutePath) ?: return@mapNotNull null
            Match(heroName, compare(candidateSignature, signature(template)))
        }.sortedByDescending { it.score }.take(limit)
    }

    fun templateCount(context: Context): Int =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all.size

    private data class Signature(val hash: BooleanArray, val histogram: FloatArray)

    private fun signature(bitmap: Bitmap): Signature {
        val scaled = Bitmap.createScaledBitmap(bitmap, SIZE, SIZE, true)
        val pixels = IntArray(SIZE * SIZE)
        scaled.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE)

        // 8x8 difference hash.
        val hash = BooleanArray(64)
        for (y in 0 until 8) {
            for (x in 0 until 8) {
                val left = luminance(pixels[y * SIZE + x * 4])
                val right = luminance(pixels[y * SIZE + (x + 1) * 4])
                hash[y * 8 + x] = left > right
            }
        }

        // Coarse RGB histogram (8 bins/channel), normalized for image size.
        val histogram = FloatArray(24)
        pixels.forEach { color ->
            histogram[(android.graphics.Color.red(color) * 8 / 256).coerceIn(0, 7)]++
            histogram[8 + (android.graphics.Color.green(color) * 8 / 256).coerceIn(0, 7)]++
            histogram[16 + (android.graphics.Color.blue(color) * 8 / 256).coerceIn(0, 7)]++
        }
        for (i in histogram.indices) histogram[i] /= pixels.size.toFloat()
        if (scaled !== bitmap) scaled.recycle()
        return Signature(hash, histogram)
    }

    private fun compare(a: Signature, b: Signature): Float {
        var equal = 0
        for (i in a.hash.indices) if (a.hash[i] == b.hash[i]) equal++
        val hashScore = equal / 64f
        var histogramIntersection = 0f
        for (i in a.histogram.indices) {
            histogramIntersection += minOf(a.histogram[i], b.histogram[i])
        }
        val histogramScore = histogramIntersection / 3f
        return (hashScore * 0.65f + histogramScore * 0.35f).coerceIn(0f, 1f)
    }

    private fun luminance(color: Int): Int =
        (android.graphics.Color.red(color) * 299 +
            android.graphics.Color.green(color) * 587 +
            android.graphics.Color.blue(color) * 114) / 1000

    private fun decode(context: Context, uri: Uri): Bitmap =
        context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream)
        } ?: throw IllegalArgumentException("Gambar tidak dapat dibuka.")

    private fun cropNormalized(source: Bitmap, rect: RectF): Bitmap {
        require(rect.left >= 0f && rect.top >= 0f && rect.right <= 1f &&
            rect.bottom <= 1f && rect.width() > 0f && rect.height() > 0f) {
            "Koordinat crop harus berada di rentang 0 sampai 1."
        }
        val left = (rect.left * source.width).toInt().coerceIn(0, source.width - 1)
        val top = (rect.top * source.height).toInt().coerceIn(0, source.height - 1)
        val right = (rect.right * source.width).toInt().coerceIn(left + 1, source.width)
        val bottom = (rect.bottom * source.height).toInt().coerceIn(top + 1, source.height)
        return Bitmap.createBitmap(source, left, top, right - left, bottom - top)
    }
}
