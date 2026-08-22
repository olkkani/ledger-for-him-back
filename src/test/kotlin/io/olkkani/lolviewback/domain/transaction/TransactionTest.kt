package io.olkkani.lolviewback.domain.transaction

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TransactionTest {
    @Test
    fun `amount can be negative for expense rows`() {
        val tx = Transaction(
            id = 1L,
            userId = 1L,
            amount = BigDecimal("-4500.00"),
            description = "스타벅스",
            occurredAt = OffsetDateTime.parse("2026-01-15T00:00:00+09:00"),
            category = "식비",
        )
        assertTrue(tx.amount < BigDecimal.ZERO)
    }

    @Test
    fun `amount can be positive for income rows`() {
        val tx = Transaction(
            id = 2L,
            userId = 1L,
            amount = BigDecimal("3000000.00"),
            description = "월급",
            occurredAt = OffsetDateTime.parse("2026-01-25T00:00:00+09:00"),
            category = "급여",
        )
        assertEquals(BigDecimal("3000000.00"), tx.amount)
    }
}
