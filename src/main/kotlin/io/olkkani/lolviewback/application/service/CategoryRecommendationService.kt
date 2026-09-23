package io.olkkani.lolviewback.application.service

import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.OffsetDateTime

data class CategoryCandidate(
    val category: String,
    val score: Double,
    val frequency: Int,
)

/**
 * Orchestrates the staged category recommendation: fetches grouped history via
 * TransactionRepository, scores each candidate with CategoryRecommendationScorer, and
 * returns the top 5 by the deterministic 3-key sort (score desc, frequency desc,
 * category asc). Stage is selected purely by whether `amount` is present — no separate
 * stage parameter, per docs/designs/transaction-category-recommendation.md.
 */
@Service
class CategoryRecommendationService(private val repository: TransactionRepository) {

    fun recommend(userId: Long, amount: BigDecimal?, targetDateTime: OffsetDateTime): List<CategoryCandidate> {
        // Repository comparisons (occurred_at < targetDateTime) are instant-based and
        // correct on the original targetDateTime. Hour/day-of-week EXTRACTION must happen
        // in Asia/Seoul (matching the repository's `AT TIME ZONE 'Asia/Seoul'` aggregates),
        // not in whatever offset targetDateTime happens to carry (e.g. UTC from the
        // controller's `OffsetDateTime.now()` default on UTC containers).
        val aggregates = repository.findCategoryAggregates(userId, targetDateTime)
        val localTarget = targetDateTime.atZoneSameInstant(SEOUL)
        val targetHour = localTarget.hour
        val targetDow = localTarget.dayOfWeek.value % 7 // Postgres DOW: 0=Sunday..6=Saturday; java DayOfWeek: 1=Monday..7=Sunday

        val scored = if (amount == null) {
            // Both sides are already normalized (TRIM(LOWER(...))) by the repository, so
            // this comparison is spelling-independent — a differently-spelled-but-same-
            // normalized-category row still matches for the prior-category boost.
            val priorCategory = repository.findMostRecentCategory(userId, targetDateTime)
            aggregates.map { agg ->
                val score = CategoryRecommendationScorer.scoreStage1(
                    targetHour = targetHour,
                    targetDow = targetDow,
                    candidateTypicalHour = radiansToHour(agg.typicalHourRadians),
                    candidateTypicalDow = radiansToDow(agg.typicalDowRadians),
                    isPriorCategory = agg.normalizedCategory == priorCategory,
                )
                CategoryCandidate(agg.category, score, agg.frequency)
            }
        } else {
            aggregates.map { agg ->
                val score = CategoryRecommendationScorer.scoreStage2(
                    targetHour = targetHour,
                    candidateTypicalHour = radiansToHour(agg.typicalHourRadians),
                    inputAmount = amount,
                    candidateAvgAmount = agg.avgAmount,
                )
                CategoryCandidate(agg.category, score, agg.frequency)
            }
        }

        return scored
            .sortedWith(
                compareByDescending<CategoryCandidate> { it.score }
                    .thenByDescending { it.frequency }
                    .thenBy { it.category },
            )
            .take(5)
    }

    private fun radiansToHour(angle: Double): Double {
        val hour = angle / (2 * Math.PI) * 24
        return if (hour < 0) hour + 24 else hour
    }

    private fun radiansToDow(angle: Double): Double {
        val dow = angle / (2 * Math.PI) * 7
        return if (dow < 0) dow + 7 else dow
    }
}
