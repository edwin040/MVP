package com.mcgg.fasthand

import android.content.Context
import android.graphics.Rect
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.max

/**
 * Screenshot OCR prototype. It reads visible text only; it does not capture the game screen
 * or perform taps. Matches are suggestions and must be verified before any game action.
 */
object HeroOcrAnalyzer {
    data class HeroMatch(
        val heroName: String,
        val recognizedText: String,
        val similarity: Float,
        val bounds: Rect?,
        val estimatedZone: String,
        val normalizedCenterX: Float,
        val normalizedCenterY: Float
    )

    data class Analysis(
        val fullText: String,
        val matches: List<HeroMatch>
    )

    fun analyze(
        context: Context,
        imageUri: Uri,
        knownHeroes: List<String>,
        onSuccess: (Analysis) -> Unit,
        onFailure: (Exception) -> Unit
    ) {
        val image = try {
            InputImage.fromFilePath(context, imageUri)
        } catch (error: Exception) {
            onFailure(error)
            return
        }

        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        recognizer.process(image)
            .addOnSuccessListener { result ->
                try {
                    val matches = mutableListOf<HeroMatch>()
                    result.textBlocks.forEach { block ->
                        block.lines.forEach { line ->
                            val best = bestMatch(line, knownHeroes) ?: return@forEach
                            if (best.second >= MIN_SIMILARITY) {
                                matches += HeroMatch(
                                    heroName = best.first,
                                    recognizedText = line.text,
                                    similarity = best.second,
                                    bounds = line.boundingBox,
                                    estimatedZone = estimateZone(line.boundingBox, image.width, image.height),
                                    normalizedCenterX = normalizedCenter(line.boundingBox, image.width, true),
                                    normalizedCenterY = normalizedCenter(line.boundingBox, image.height, false)
                                )
                            }
                        }
                    }
                    onSuccess(Analysis(result.text, matches.sortedByDescending { it.similarity }))
                } finally {
                    recognizer.close()
                }
            }
            .addOnFailureListener { error ->
                recognizer.close()
                onFailure(error)
            }
    }


    /**
     * Provisional landscape layout heuristic only. Coordinates are normalized so it scales
     * across resolutions. It must not be used to trigger game actions until calibrated.
     */
    private fun normalizedCenter(bounds: Rect?, dimension: Int, horizontal: Boolean): Float {
        if (bounds == null || dimension <= 0) return -1f
        val center = if (horizontal) bounds.centerX() else bounds.centerY()
        return (center.toFloat() / dimension).coerceIn(0f, 1f)
    }

    private fun estimateZone(bounds: Rect?, width: Int, height: Int): String {
        if (bounds == null || width <= 0 || height <= 0) return "UNKNOWN"
        if (width < height) return "UNKNOWN • PORTRAIT"
        val y = bounds.centerY().toFloat() / height
        return when {
            y >= 0.80f && y <= 0.98f -> "SHOP? • PERKIRAAN"
            y >= 0.65f && y < 0.80f -> "BENCH? • PERKIRAAN"
            y >= 0.18f && y < 0.65f -> "BOARD? • PERKIRAAN"
            else -> "UNKNOWN"
        }
    }

    private fun bestMatch(line: Text.Line, knownHeroes: List<String>): Pair<String, Float>? {
        val raw = normalize(line.text)
        if (raw.isBlank()) return null

        var bestName: String? = null
        var bestScore = 0f
        for (hero in knownHeroes) {
            val target = normalize(hero)
            if (target.isBlank()) continue
            val score = if (raw.contains(target)) {
                1f
            } else {
                bestWindowScore(raw, target)
            }
            if (score > bestScore) {
                bestName = hero
                bestScore = score
            }
        }
        return bestName?.let { it to bestScore }
    }

    private fun bestWindowScore(raw: String, target: String): Float {
        val words = raw.split(' ').filter { it.isNotBlank() }
        val targetWords = target.split(' ').filter { it.isNotBlank() }
        val expected = targetWords.size
        var best = 0f
        val minWindow = max(1, expected - 1)
        val maxWindow = minOf(words.size, expected + 1)
        for (size in minWindow..maxWindow) {
            for (start in 0..(words.size - size)) {
                val candidate = words.subList(start, start + size).joinToString("")
                val score = similarity(candidate, target.replace(" ", ""))
                if (score > best) best = score
            }
        }
        return best
    }

    private fun normalize(value: String): String =
        value.lowercase().replace(Regex("[^a-z0-9]+"), " ").trim().replace(Regex("\\s+"), " ")

    private fun similarity(a: String, b: String): Float {
        if (a == b) return 1f
        if (a.isEmpty() || b.isEmpty()) return 0f
        val previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                val cost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + cost
                )
            }
            val swap = previous
            for (j in swap.indices) swap[j] = current[j]
            current = IntArray(b.length + 1)
        }
        val distance = previous[b.length]
        return 1f - distance.toFloat() / max(a.length, b.length)
    }

    private const val MIN_SIMILARITY = 0.80f
}
