package io.olkkani.lolviewback.application.service

import io.mockk.every
import io.mockk.mockk
import java.math.BigDecimal
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CategoryRecommendationServiceTest {

    private val repository = mockk<TransactionRepository>()
    private val service = CategoryRecommendationService(repository)
    private val targetDateTime = OffsetDateTime.parse("2026-06-01T12:00:00+09:00")

    private fun aggregate(
        category: String,
        avgAmount: String,
        hourRad: Double,
        dowRad: Double,
        freq: Int,
        normalizedCategory: String = category.trim().lowercase(),
    ) = CategoryAggregate(category, normalizedCategory, BigDecimal(avgAmount), hourRad, dowRad, freq)

    @Test
    fun `stage 1 selected when amount is null, uses prior category boost`() {
        every { repository.findCategoryAggregates(1L, targetDateTime) } returns listOf(
            aggregate("식비", "10000", hourRad = 0.0, dowRad = 0.0, freq = 5),
            aggregate("교통", "3000", hourRad = 0.0, dowRad = 0.0, freq = 5),
        )
        every { repository.findMostRecentCategory(1L, targetDateTime) } returns "교통"

        val result = service.recommend(userId = 1L, amount = null, targetDateTime = targetDateTime)

        // "교통" gets the prior-category boost; with equal time/dow signal it must outrank "식비"
        assertEquals("교통", result.first().category)
    }

    @Test
    fun `stage 2 selected when amount is present, prior category boost has no effect`() {
        every { repository.findCategoryAggregates(1L, targetDateTime) } returns listOf(
            aggregate("식비", "10000", hourRad = 0.0, dowRad = 0.0, freq = 5),
            aggregate("교통", "3000", hourRad = 0.0, dowRad = 0.0, freq = 5),
        )
        every { repository.findMostRecentCategory(1L, targetDateTime) } returns "교통"

        val result = service.recommend(userId = 1L, amount = BigDecimal("9500"), targetDateTime = targetDateTime)

        // amount 9500 is much closer to "식비" avg 10000 than "교통" avg 3000 -> 식비 must win despite 교통 being prior category
        assertEquals("식비", result.first().category)
    }

    @Test
    fun `stage 2 never calls findMostRecentCategory`() {
        every { repository.findCategoryAggregates(1L, targetDateTime) } returns listOf(
            aggregate("식비", "10000", hourRad = 0.0, dowRad = 0.0, freq = 1),
        )

        service.recommend(userId = 1L, amount = BigDecimal("10000"), targetDateTime = targetDateTime)

        io.mockk.verify(exactly = 0) { repository.findMostRecentCategory(any(), any()) }
    }

    @Test
    fun `returns at most 5 candidates`() {
        every { repository.findCategoryAggregates(1L, targetDateTime) } returns (1..8).map {
            aggregate("cat$it", "1000", hourRad = 0.0, dowRad = 0.0, freq = it)
        }
        every { repository.findMostRecentCategory(1L, targetDateTime) } returns null

        val result = service.recommend(userId = 1L, amount = null, targetDateTime = targetDateTime)

        assertTrue(result.size <= 5)
    }

    @Test
    fun `returns fewer than 5 without padding when fewer categories qualify`() {
        every { repository.findCategoryAggregates(1L, targetDateTime) } returns listOf(
            aggregate("식비", "10000", hourRad = 0.0, dowRad = 0.0, freq = 1),
        )
        every { repository.findMostRecentCategory(1L, targetDateTime) } returns null

        val result = service.recommend(userId = 1L, amount = null, targetDateTime = targetDateTime)

        assertEquals(1, result.size)
    }

    @Test
    fun `returns empty list when history is empty`() {
        every { repository.findCategoryAggregates(1L, targetDateTime) } returns emptyList()
        every { repository.findMostRecentCategory(1L, targetDateTime) } returns null

        val result = service.recommend(userId = 1L, amount = null, targetDateTime = targetDateTime)

        assertEquals(emptyList(), result)
    }

    @Test
    fun `UTC target instant scores identically to its Seoul-local equivalent`() {
        val utcTarget = OffsetDateTime.parse("2026-06-01T03:00:00Z") // == 2026-06-01T12:00:00+09:00
        val seoulTarget = OffsetDateTime.parse("2026-06-01T12:00:00+09:00")

        // Candidate typical hour/dow chosen to be off-center so a 9h shift would change the score.
        val candidates = listOf(
            aggregate("식비", "10000", hourRad = 0.0, dowRad = 0.0, freq = 5),
            aggregate("교통", "3000", hourRad = 0.0, dowRad = 0.0, freq = 5),
        )
        every { repository.findCategoryAggregates(1L, utcTarget) } returns candidates
        every { repository.findCategoryAggregates(1L, seoulTarget) } returns candidates
        every { repository.findMostRecentCategory(1L, utcTarget) } returns "교통"
        every { repository.findMostRecentCategory(1L, seoulTarget) } returns "교통"

        val utcResult = service.recommend(userId = 1L, amount = null, targetDateTime = utcTarget)
        val seoulResult = service.recommend(userId = 1L, amount = null, targetDateTime = seoulTarget)

        assertEquals(seoulResult.map { it.category to it.score }, utcResult.map { it.category to it.score })
    }

    @Test
    fun `prior category boost matches on normalized category, not raw spelling`() {
        every { repository.findCategoryAggregates(1L, targetDateTime) } returns listOf(
            // Most-recent-original-spelling has a trailing space, but its normalized key matches.
            aggregate("식비 ", "10000", hourRad = 0.0, dowRad = 0.0, freq = 5, normalizedCategory = "식비"),
            aggregate("교통", "3000", hourRad = 0.0, dowRad = 0.0, freq = 5, normalizedCategory = "교통"),
        )
        // findMostRecentCategory now returns the normalized key (no trailing space).
        every { repository.findMostRecentCategory(1L, targetDateTime) } returns "식비"

        val result = service.recommend(userId = 1L, amount = null, targetDateTime = targetDateTime)

        assertEquals("식비 ", result.first().category)
    }

    @Test
    fun `ties break by frequency descending then category ascending`() {
        // Equal hour/dow/no-prior signal for both -> identical score; frequency then alpha order decides.
        every { repository.findCategoryAggregates(1L, targetDateTime) } returns listOf(
            aggregate("나중", "10000", hourRad = 0.0, dowRad = 0.0, freq = 2),
            aggregate("먼저", "10000", hourRad = 0.0, dowRad = 0.0, freq = 5),
        )
        every { repository.findMostRecentCategory(1L, targetDateTime) } returns null

        val result = service.recommend(userId = 1L, amount = null, targetDateTime = targetDateTime)

        assertEquals(listOf("먼저", "나중"), result.map { it.category })
    }
}
