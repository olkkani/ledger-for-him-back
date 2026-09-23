package io.olkkani.lolviewback.application.service

import java.math.BigDecimal
import kotlin.math.abs
import kotlin.math.min

/**
 * Pure, Spring-free scoring for category recommendations. Two structurally distinct
 * formulas — Stage 1 (no amount yet) and Stage 2 (amount entered) — per the staged
 * replacement model in docs/designs/transaction-category-recommendation.md. The
 * prior-category term exists ONLY in Stage 1; it is not present, not zero-weighted,
 * in Stage 2.
 *
 * Weight values below are PLACEHOLDERS pending a backtest against real transaction
 * history (see design doc Open Questions — "Exact weight values"). Chosen so each
 * stage's weights sum to 1.0 for interpretability; not tuned against any data yet.
 */
object CategoryRecommendationScorer {

    const val W_TIME_STAGE1 = 0.4
    const val W_DOW_STAGE1 = 0.2
    const val W_PRIOR_STAGE1 = 0.4

    const val W_TIME_STAGE2 = 0.3
    const val W_AMOUNT_STAGE2 = 0.7

    private val WEEKEND_DOW = setOf(0, 6) // Postgres EXTRACT(DOW): 0=Sunday..6=Saturday

    fun scoreStage1(
        targetHour: Int,
        targetDow: Int,
        candidateTypicalHour: Double,
        candidateTypicalDow: Double,
        isPriorCategory: Boolean,
    ): Double {
        val time = timeSim(targetHour, candidateTypicalHour)
        val dow = dowSim(targetDow, candidateTypicalDow)
        val prior = if (isPriorCategory) 1.0 else 0.0
        return W_TIME_STAGE1 * time + W_DOW_STAGE1 * dow + W_PRIOR_STAGE1 * prior
    }

    fun scoreStage2(
        targetHour: Int,
        candidateTypicalHour: Double,
        inputAmount: BigDecimal,
        candidateAvgAmount: BigDecimal,
    ): Double {
        val time = timeSim(targetHour, candidateTypicalHour)
        val amount = amountSim(inputAmount, candidateAvgAmount)
        return W_TIME_STAGE2 * time + W_AMOUNT_STAGE2 * amount
    }

    private fun timeSim(targetHour: Int, candidateTypicalHour: Double): Double {
        val diff = abs(targetHour - candidateTypicalHour)
        val hourDistance = min(diff, 24 - diff)
        return 1 - min(hourDistance / 12.0, 1.0)
    }

    private fun dowSim(targetDow: Int, candidateTypicalDow: Double): Double {
        val targetIsWeekend = targetDow in WEEKEND_DOW
        val candidateIsWeekend = candidateTypicalDow.toInt() in WEEKEND_DOW
        return if (targetIsWeekend == candidateIsWeekend) 1.0 else 0.3
    }

    private fun amountSim(inputAmount: BigDecimal, candidateAvgAmount: BigDecimal): Double {
        val input = inputAmount.abs()
        val avg = candidateAvgAmount.abs()
        val denominator = maxOf(avg, BigDecimal.ONE)
        val diff = (input - avg).abs()
        val ratio = (diff.toDouble() / denominator.toDouble())
        return 1 - min(ratio, 1.0)
    }
}
