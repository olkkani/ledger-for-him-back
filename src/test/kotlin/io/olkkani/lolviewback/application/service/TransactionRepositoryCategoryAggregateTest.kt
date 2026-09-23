package io.olkkani.lolviewback.application.service

import org.jooq.DSLContext
import org.jooq.generated.Tables.TRANSACTIONS
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Testcontainers
@SpringBootTest
class TransactionRepositoryCategoryAggregateTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        @JvmStatic
        @org.springframework.test.context.DynamicPropertySource
        fun props(registry: org.springframework.test.context.DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired
    lateinit var repository: TransactionRepository

    @Autowired
    lateinit var dsl: DSLContext

    @BeforeEach
    fun cleanTable() {
        dsl.deleteFrom(TRANSACTIONS).execute()
    }

    private fun tx(
        id: Long,
        category: String,
        occurredAt: String,
        amount: BigDecimal = BigDecimal("-10000"),
        description: String = "desc-$id",
    ) = Transaction(
        id = id,
        userId = 1L,
        amount = amount,
        description = description,
        occurredAt = OffsetDateTime.parse(occurredAt),
        category = category,
    )

    @Test
    fun `findCategoryAggregates merges casing and whitespace variants into one group`() {
        repository.insertAll(
            listOf(
                tx(1, "식비", "2026-01-10T12:00:00+09:00"),
                tx(2, "식비 ", "2026-01-11T12:00:00+09:00"),
                tx(3, "식비", "2026-01-12T12:00:00+09:00"),
            ),
        )

        val aggregates = repository.findCategoryAggregates(userId = 1L, targetDateTime = OffsetDateTime.parse("2026-06-01T00:00:00+09:00"))

        assertEquals(1, aggregates.size)
        assertEquals(3, aggregates.single().frequency)
    }

    @Test
    fun `findCategoryAggregates returns most-recently-used original spelling for the group`() {
        repository.insertAll(
            listOf(
                tx(1, "식비", "2026-01-10T12:00:00+09:00"),
                tx(2, "식비 ", "2026-01-12T12:00:00+09:00"), // latest occurred_at, trailing space
            ),
        )

        val aggregates = repository.findCategoryAggregates(userId = 1L, targetDateTime = OffsetDateTime.parse("2026-06-01T00:00:00+09:00"))

        assertEquals("식비 ", aggregates.single().category)
    }

    @Test
    fun `findCategoryAggregates excludes rows at or after targetDateTime`() {
        repository.insertAll(
            listOf(
                tx(1, "교통", "2026-01-10T12:00:00+09:00"),
                tx(2, "교통", "2026-06-01T12:00:00+09:00"), // after cutoff
            ),
        )

        val aggregates = repository.findCategoryAggregates(userId = 1L, targetDateTime = OffsetDateTime.parse("2026-02-01T00:00:00+09:00"))

        assertEquals(1, aggregates.single().frequency)
    }

    @Test
    fun `findCategoryAggregates computes avgAmount by absolute value`() {
        repository.insertAll(
            listOf(
                tx(1, "여가", "2026-01-10T12:00:00+09:00", amount = BigDecimal("-4000")),
                tx(2, "여가", "2026-01-11T12:00:00+09:00", amount = BigDecimal("-6000")),
            ),
        )

        val aggregates = repository.findCategoryAggregates(userId = 1L, targetDateTime = OffsetDateTime.parse("2026-06-01T00:00:00+09:00"))

        assertEquals(BigDecimal("5000.00"), aggregates.single().avgAmount.setScale(2))
    }

    @Test
    fun `findCategoryAggregates hour extraction reflects Asia_Seoul not UTC near midnight`() {
        // 2026-01-10T23:30+09:00 == 2026-01-10T14:30 UTC. If EXTRACT used UTC, the hour would read 14, not 23.
        repository.insertAll(listOf(tx(1, "심야", "2026-01-10T23:30:00+09:00")))

        val aggregates = repository.findCategoryAggregates(userId = 1L, targetDateTime = OffsetDateTime.parse("2026-06-01T00:00:00+09:00"))

        // circular mean of a single point at hour 23.5 -> angle should convert back to ~23.5, not ~14.5
        val hourAngle = aggregates.single().typicalHourRadians
        val recoveredHour = (Math.toDegrees(hourAngle) / 15.0).let { if (it < 0) it + 24 else it } // 360deg/24h = 15deg/h
        assertTrue(recoveredHour > 20.0, "expected KST hour ~23.5, got $recoveredHour")
    }

    @Test
    fun `findMostRecentCategory returns the category of the latest transaction before targetDateTime`() {
        repository.insertAll(
            listOf(
                tx(1, "식비", "2026-01-10T12:00:00+09:00"),
                tx(2, "교통", "2026-01-15T12:00:00+09:00"),
            ),
        )

        val result = repository.findMostRecentCategory(userId = 1L, targetDateTime = OffsetDateTime.parse("2026-06-01T00:00:00+09:00"))

        assertEquals("교통", result)
    }

    @Test
    fun `findMostRecentCategory excludes transactions at or after targetDateTime`() {
        repository.insertAll(
            listOf(
                tx(1, "식비", "2026-01-10T12:00:00+09:00"),
                tx(2, "교통", "2026-06-15T12:00:00+09:00"), // after cutoff
            ),
        )

        val result = repository.findMostRecentCategory(userId = 1L, targetDateTime = OffsetDateTime.parse("2026-02-01T00:00:00+09:00"))

        assertEquals("식비", result)
    }

    @Test
    fun `findMostRecentCategory returns null when the user has no prior history`() {
        val result = repository.findMostRecentCategory(userId = 1L, targetDateTime = OffsetDateTime.parse("2026-02-01T00:00:00+09:00"))

        assertNull(result)
    }
}
