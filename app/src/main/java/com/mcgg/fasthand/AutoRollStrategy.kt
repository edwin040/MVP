package com.mcgg.fasthand

/**
 * Deterministic decision rules for the MCGG auto-roll loop.
 *
 * This class deliberately does not tap the game. A screen-recognition layer must supply
 * detections and a separately permissioned interaction layer must execute the returned
 * actions. Unknown/low-confidence detections are never sold.
 */
object AutoRollStrategy {
    enum class Commander { GUINEVERE, OTHER, UNKNOWN }
    enum class Zone { SHOP, BENCH, BOARD }
    enum class ActionType {
        BUY_LOCKED_HERO,
        BUY_FREE_GUINEVERE_HERO,
        SELL_NON_LOCKED_HERO,
        REFRESH_SHOP,
        WAIT_FOR_RECOGNITION,
        STOP_TARGETS_COMPLETE,
        WAIT_FOR_LEVEL_NINE,
        NO_OP
    }

    data class HeroDetection(
        val name: String,
        val zone: Zone,
        val confidence: Float,
        val isFree: Boolean = false,
        val slot: Int = -1
    )

    data class MatchSnapshot(
        val commander: Commander,
        val commanderConfidence: Float,
        val commanderLevel: Int,
        val heroes: List<HeroDetection>,
        val lockedHeroes: Set<String>,
        val incompleteTargets: Set<String>,
        val autoEnabled: Boolean
    )

    data class PlannedAction(
        val type: ActionType,
        val heroName: String? = null,
        val zone: Zone? = null,
        val slot: Int = -1,
        val reason: String
    )

    private const val HERO_CONFIDENCE = 0.97f
    private const val COMMANDER_CONFIDENCE = 0.97f

    fun plan(snapshot: MatchSnapshot): List<PlannedAction> {
        if (!snapshot.autoEnabled) {
            return listOf(PlannedAction(ActionType.NO_OP, reason = "Auto Roll nonaktif"))
        }
        if (snapshot.commanderLevel < 9) {
            return listOf(PlannedAction(ActionType.WAIT_FOR_LEVEL_NINE,
                reason = "Menunggu Commander level 9"))
        }
        if (snapshot.commander == Commander.UNKNOWN ||
            snapshot.commanderConfidence < COMMANDER_CONFIDENCE) {
            return listOf(PlannedAction(ActionType.WAIT_FOR_RECOGNITION,
                reason = "Commander belum dikenali dengan yakin"))
        }
        if (snapshot.incompleteTargets.isEmpty()) {
            return listOf(PlannedAction(ActionType.STOP_TARGETS_COMPLETE,
                reason = "Semua target LOCK sudah selesai"))
        }

        val actions = mutableListOf<PlannedAction>()

        // Never sell a hero unless both its identity and location are confidently detected.
        // Apply this to both the bench and the deployed board as requested.
        snapshot.heroes.asSequence()
            .filter { it.zone == Zone.BENCH || it.zone == Zone.BOARD }
            .filter { it.confidence >= HERO_CONFIDENCE }
            .filter { it.name !in snapshot.lockedHeroes }
            .forEach {
                actions += PlannedAction(
                    ActionType.SELL_NON_LOCKED_HERO, it.name, it.zone, it.slot,
                    "Hero teridentifikasi jelas dan tidak termasuk LOCK"
                )
            }

        // Buy all visible locked targets, but only when their identity is certain.
        snapshot.heroes.asSequence()
            .filter { it.zone == Zone.SHOP }
            .filter { it.confidence >= HERO_CONFIDENCE }
            .filter { it.name in snapshot.incompleteTargets && it.name in snapshot.lockedHeroes }
            .forEach {
                actions += PlannedAction(
                    ActionType.BUY_LOCKED_HERO, it.name, Zone.SHOP, it.slot,
                    "Hero target LOCK ditemukan di toko"
                )
            }

        // Guinevere's free shop hero is handled specially: buy it, then sell it only if
        // it is not LOCKed. A LOCKed free hero is retained as a target copy.
        if (snapshot.commander == Commander.GUINEVERE) {
            snapshot.heroes.asSequence()
                .filter { it.zone == Zone.SHOP && it.isFree && it.confidence >= HERO_CONFIDENCE }
                .forEach {
                    actions += PlannedAction(
                        ActionType.BUY_FREE_GUINEVERE_HERO, it.name, Zone.SHOP, it.slot,
                        if (it.name in snapshot.lockedHeroes)
                            "Hero gratis Guinevere termasuk LOCK; beli dan simpan"
                        else
                            "Hero gratis Guinevere; beli dahulu, lalu jual setelah terdeteksi di bench"
                    )
                }
        }

        if (actions.isEmpty()) {
            actions += PlannedAction(
                ActionType.REFRESH_SHOP,
                reason = "Tidak ada target LOCK yang terlihat; refresh untuk mencari target"
            )
        }
        return actions
    }

    /** True only when a confidently identified bench/board hero is safe to sell. */
    fun maySell(detection: HeroDetection, lockedHeroes: Set<String>): Boolean =
        (detection.zone == Zone.BENCH || detection.zone == Zone.BOARD) &&
            detection.confidence >= HERO_CONFIDENCE &&
            detection.name !in lockedHeroes
}
