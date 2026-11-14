package com.mcgg.fasthand

/**
 * Conservative, single-step decision engine for the MCGG automation loop.
 *
 * The caller must obtain fresh screen detections after EVERY action. This engine never
 * batches taps: one action is returned so the caller can verify the resulting game state.
 * Unknown or low-confidence detections are never sold or purchased.
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
        val slot: Int = -1,
        /** Price read from the current shop frame; null means unknown. */
        val price: Int? = null
    )

    data class MatchSnapshot(
        val commander: Commander,
        val commanderConfidence: Float,
        val commanderLevel: Int,
        val heroes: List<HeroDetection>,
        val lockedHeroes: Set<String>,
        val incompleteTargets: Set<String>,
        val autoEnabled: Boolean,
        /** Gold must come from a stable, validated HUD reading. */
        val goldAmount: Int? = null,
        val goldConfidence: Float = 0f,
        /** Never assume game costs: caller must supply verified values. */
        val refreshCost: Int? = null
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
    private const val GOLD_CONFIDENCE = 0.95f

    private fun goldIsReliable(snapshot: MatchSnapshot): Boolean =
        snapshot.goldAmount != null && snapshot.goldAmount >= 0 &&
            snapshot.goldConfidence >= GOLD_CONFIDENCE

    private fun waitForGold(reason: String) = listOf(
        PlannedAction(ActionType.WAIT_FOR_RECOGNITION, reason = reason)
    )

    private fun normalized(value: String): String =
        value.trim().lowercase().replace(Regex("[^a-z0-9]"), "")

    private fun isLocked(name: String, locked: Set<String>): Boolean {
        val key = normalized(name)
        return locked.any { normalized(it) == key }
    }

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

        val known = snapshot.heroes.filter { it.confidence >= HERO_CONFIDENCE }
        val uncertainShop = snapshot.heroes.any {
            it.zone == Zone.SHOP && it.confidence < HERO_CONFIDENCE
        }

        // Guinevere's free hero MUST be bought first. It is never sold while still in SHOP.
        if (snapshot.commander == Commander.GUINEVERE) {
            val freeHero = known.firstOrNull { it.zone == Zone.SHOP && it.isFree }
            if (freeHero != null && !isLocked(freeHero.name, snapshot.lockedHeroes)) {
                return listOf(PlannedAction(
                    ActionType.BUY_FREE_GUINEVERE_HERO, freeHero.name, Zone.SHOP, freeHero.slot,
                    "Beli hero gratis terlebih dahulu; evaluasi ulang setelah masuk bench"
                ))
            }
        }

        // Only sell after the bought free hero is positively observed on the bench.
        // The same conservative rule applies to all other non-LOCK bench/board heroes.
        val sellCandidate = known.firstOrNull {
            (it.zone == Zone.BENCH || it.zone == Zone.BOARD) &&
                !isLocked(it.name, snapshot.lockedHeroes)
        }
        if (sellCandidate != null) {
            return listOf(PlannedAction(
                ActionType.SELL_NON_LOCKED_HERO, sellCandidate.name,
                sellCandidate.zone, sellCandidate.slot,
                "Identitas dan lokasi terverifikasi; hero bukan anggota LOCK"
            ))
        }

        // Process one locked shop target at a time, then require a fresh frame.
        val target = known.firstOrNull {
            it.zone == Zone.SHOP &&
                snapshot.incompleteTargets.any { wanted -> normalized(wanted) == normalized(it.name) } &&
                isLocked(it.name, snapshot.lockedHeroes)
        }
        if (target != null) {
            if (!goldIsReliable(snapshot)) {
                return waitForGold("Gold belum terbaca stabil; pembelian ditahan demi keamanan")
            }
            val price = target.price
                ?: return waitForGold("Harga hero belum terbaca; pembelian ditahan")
            if (snapshot.goldAmount!! < price) {
                return waitForGold("Gold tidak cukup untuk membeli ${target.name}")
            }
            return listOf(PlannedAction(
                ActionType.BUY_LOCKED_HERO, target.name, Zone.SHOP, target.slot,
                "Gold dan harga terverifikasi; beli satu lalu verifikasi ulang"
            ))
        }

        // Never refresh while a shop slot is visible but its identity is uncertain.
        if (uncertainShop) {
            return listOf(PlannedAction(ActionType.WAIT_FOR_RECOGNITION,
                reason = "Ada slot toko yang belum dikenali; tahan refresh sampai pemeriksaan ulang"))
        }

        // If Guinevere has a free slot but it could not be identified, do not skip it.
        if (snapshot.commander == Commander.GUINEVERE &&
            snapshot.heroes.any { it.zone == Zone.SHOP && it.isFree }) {
            return listOf(PlannedAction(ActionType.WAIT_FOR_RECOGNITION,
                reason = "Hero gratis Guinevere belum dikenali dengan yakin"))
        }

        if (!goldIsReliable(snapshot)) {
            return waitForGold("Gold belum terbaca stabil; refresh ditahan demi keamanan")
        }
        val refreshCost = snapshot.refreshCost
            ?: return waitForGold("Biaya refresh belum dikonfigurasi/terverifikasi")
        if (snapshot.goldAmount!! < refreshCost) {
            return waitForGold("Gold tidak cukup untuk refresh shop")
        }
        return listOf(PlannedAction(
            ActionType.REFRESH_SHOP,
            reason = "Gold mencukupi dan biaya refresh terverifikasi"
        ))
    }

    /** True only when a confidently identified bench/board hero is safe to sell. */
    fun maySell(detection: HeroDetection, lockedHeroes: Set<String>): Boolean =
        (detection.zone == Zone.BENCH || detection.zone == Zone.BOARD) &&
            detection.confidence >= HERO_CONFIDENCE &&
            !isLocked(detection.name, lockedHeroes)
}
