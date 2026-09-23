package io.olkkani.lolviewback.application.service

import java.math.BigDecimal
import kotlin.test.Test
import kotlin.test.assertEquals

class CategoryRecommendationScorerTest {

    // --- timeSim / hourDistance (circular) ---

    @Test
    fun `timeSim is 1 when target hour equals candidate typical hour`() {
        val score = CategoryRecommendationScorer.scoreStage1(
            targetHour = 12,
            targetDow = 1,
            candidateTypicalHour = 12.0,
            candidateTypicalDow = 1.0,
            isPriorCategory = false,
        )
        // w_time * 1.0 + w_dow * 1.0 + w_prior * 0
        val expected = CategoryRecommendationScorer.W_TIME_STAGE1 * 1.0 + CategoryRecommendationScorer.W_DOW_STAGE1 * 1.0
        assertEquals(expected, score, 1e-9)
    }

    @Test
    fun `hourDistance wraps midnight so 23h vs 1h is distance 2, not 22`() {
        // 23:00 target vs candidate typical hour 1:00 -> circular distance 2 -> timeSim = 1 - 2/12
        val score = CategoryRecommendationScorer.scoreStage1(
            targetHour = 23,
            targetDow = 1,
            candidateTypicalHour = 1.0,
            candidateTypicalDow = 1.0, // same dow bucket -> dowSim = 1
            isPriorCategory = false,
        )
        val expectedTimeSim = 1 - (2.0 / 12)
        val expected = CategoryRecommendationScorer.W_TIME_STAGE1 * expectedTimeSim + CategoryRecommendationScorer.W_DOW_STAGE1 * 1.0
        assertEquals(expected, score, 1e-9)
    }

    @Test
    fun `hourDistance at opposite time of day (12h apart) gives timeSim of 0`() {
        val score = CategoryRecommendationScorer.scoreStage1(
            targetHour = 8,
            targetDow = 1,
            candidateTypicalHour = 20.0, // distance 12
            candidateTypicalDow = 1.0,
            isPriorCategory = false,
        )
        val expected = CategoryRecommendationScorer.W_TIME_STAGE1 * 0.0 + CategoryRecommendationScorer.W_DOW_STAGE1 * 1.0
        assertEquals(expected, score, 1e-9)
    }

    // --- dowSim: weekday/weekend bucket ---

    @Test
    fun `dowSim is 1 when target and candidate are both weekdays`() {
        // ISO/Postgres DOW convention used throughout: 0=Sunday..6=Saturday. Bucket: 0 and 6 are weekend, 1-5 weekday.
        val score = CategoryRecommendationScorer.scoreStage1(
            targetHour = 12,
            targetDow = 2, // Tuesday
            candidateTypicalHour = 12.0,
            candidateTypicalDow = 4.0, // Thursday, same weekday bucket
            isPriorCategory = false,
        )
        val expected = CategoryRecommendationScorer.W_TIME_STAGE1 * 1.0 + CategoryRecommendationScorer.W_DOW_STAGE1 * 1.0
        assertEquals(expected, score, 1e-9)
    }

    @Test
    fun `dowSim is 0_3 when target is weekday and candidate typical is weekend`() {
        val score = CategoryRecommendationScorer.scoreStage1(
            targetHour = 12,
            targetDow = 2, // Tuesday (weekday)
            candidateTypicalHour = 12.0,
            candidateTypicalDow = 6.0, // Saturday (weekend)
            isPriorCategory = false,
        )
        val expected = CategoryRecommendationScorer.W_TIME_STAGE1 * 1.0 + CategoryRecommendationScorer.W_DOW_STAGE1 * 0.3
        assertEquals(expected, score, 1e-9)
    }

    // --- Stage 1: prior-category boost ---

    @Test
    fun `stage1 adds the full prior-category weight when isPriorCategory is true`() {
        val withPrior = CategoryRecommendationScorer.scoreStage1(
            targetHour = 12, targetDow = 1, candidateTypicalHour = 12.0, candidateTypicalDow = 1.0, isPriorCategory = true,
        )
        val withoutPrior = CategoryRecommendationScorer.scoreStage1(
            targetHour = 12, targetDow = 1, candidateTypicalHour = 12.0, candidateTypicalDow = 1.0, isPriorCategory = false,
        )
        assertEquals(CategoryRecommendationScorer.W_PRIOR_STAGE1, withPrior - withoutPrior, 1e-9)
    }

    // --- Stage 2: amount similarity, structurally no prior-category term ---

    @Test
    fun `stage2 score equals time and amount terms only, avg above input`() {
        val score = CategoryRecommendationScorer.scoreStage2(
            targetHour = 12,
            candidateTypicalHour = 12.0,
            inputAmount = BigDecimal("10000"),
            candidateAvgAmount = BigDecimal("15000"),
        )
        // amountSim = 1 - min(|10000-15000|/max(15000,1), 1) = 1 - 5000/15000
        val expectedAmountSim = 1 - (5000.0 / 15000.0)
        val expected = CategoryRecommendationScorer.W_TIME_STAGE2 * 1.0 + CategoryRecommendationScorer.W_AMOUNT_STAGE2 * expectedAmountSim
        assertEquals(expected, score, 1e-9)
    }

    @Test
    fun `stage2 amountSim is sign-independent, negative avgAmount still produces a sensible score`() {
        val score = CategoryRecommendationScorer.scoreStage2(
            targetHour = 12,
            candidateTypicalHour = 12.0,
            inputAmount = BigDecimal("-10000"),
            candidateAvgAmount = BigDecimal("-10000"),
        )
        // abs(input)=10000, abs(avg)=10000 -> amountSim = 1
        val expected = CategoryRecommendationScorer.W_TIME_STAGE2 * 1.0 + CategoryRecommendationScorer.W_AMOUNT_STAGE2 * 1.0
        assertEquals(expected, score, 1e-9)
    }

    @Test
    fun `stage2 amountSim when avg is below input`() {
        val score = CategoryRecommendationScorer.scoreStage2(
            targetHour = 12,
            candidateTypicalHour = 12.0,
            inputAmount = BigDecimal("20000"),
            candidateAvgAmount = BigDecimal("5000"),
        )
        // amountSim = 1 - min(|20000-5000|/max(5000,1),1) = 1 - 1 = 0 (clamped)
        val expected = CategoryRecommendationScorer.W_TIME_STAGE2 * 1.0 + CategoryRecommendationScorer.W_AMOUNT_STAGE2 * 0.0
        assertEquals(expected, score, 1e-9)
    }

    @Test
    fun `stage2 amountSim when avg equals input is 1`() {
        val score = CategoryRecommendationScorer.scoreStage2(
            targetHour = 12,
            candidateTypicalHour = 12.0,
            inputAmount = BigDecimal("7000"),
            candidateAvgAmount = BigDecimal("7000"),
        )
        val expected = CategoryRecommendationScorer.W_TIME_STAGE2 * 1.0 + CategoryRecommendationScorer.W_AMOUNT_STAGE2 * 1.0
        assertEquals(expected, score, 1e-9)
    }
}
