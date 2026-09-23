package io.olkkani.lolviewback.application.service

import java.math.BigDecimal

/**
 * One TRIM(LOWER(category))-grouped row of the user's transaction history, aggregated
 * for recommendation scoring. `category` is the most-recently-used ORIGINAL spelling
 * for the group (not the normalized key) — same convention as the sibling
 * description-recommendation design.
 *
 * typicalHourRadians / typicalDowRadians are circular-mean angles (see
 * TransactionRepository.findCategoryAggregates); callers convert back to a 0-23 hour
 * or 0-6 day-of-week before calling into CategoryRecommendationScorer.
 */
data class CategoryAggregate(
    val category: String,
    val avgAmount: BigDecimal,
    val typicalHourRadians: Double,
    val typicalDowRadians: Double,
    val frequency: Int,
)
