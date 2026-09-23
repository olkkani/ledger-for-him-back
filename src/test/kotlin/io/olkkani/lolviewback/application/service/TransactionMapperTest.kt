package io.olkkani.lolviewback.application.service

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlin.test.assertEquals

class TransactionMapperTest {

    private val mapper = TransactionMapper(idGenerator = { 42L })

    private fun validatedRow(
        incomeOrExpense: String,
        amount: BigDecimal = BigDecimal("4500"),
        date: LocalDate = LocalDate.of(2026, 1, 15),
    ) = ValidatedRow(
        rowIndex = 2,
        date = date,
        category = "식비",
        description = "스타벅스",
        incomeOrExpense = incomeOrExpense,
        amount = amount,
    )

    @Test
    fun `지출 rows are negated`() {
        val tx = mapper.map(validatedRow("지출"), userId = 1L)
        assertEquals(BigDecimal("-4500"), tx.amount)
    }

    @Test
    fun `수입 rows stay positive`() {
        val tx = mapper.map(validatedRow("수입"), userId = 1L)
        assertEquals(BigDecimal("4500"), tx.amount)
    }

    @Test
    fun `occurredAt is midnight Asia-Seoul on the source date`() {
        val tx = mapper.map(validatedRow("지출", date = LocalDate.of(2026, 1, 15)), userId = 1L)
        val expected = OffsetDateTime.of(2026, 1, 15, 0, 0, 0, 0, ZoneId.of("Asia/Seoul").rules.getOffset(Instant.now()))
        assertEquals(expected.toInstant(), tx.occurredAt.toInstant())
    }

    @Test
    fun `id and userId are passed through from the generator and argument`() {
        val tx = mapper.map(validatedRow("수입"), userId = 7L)
        assertEquals(42L, tx.id)
        assertEquals(7L, tx.userId)
    }

    @Test
    fun `category and description pass through unchanged`() {
        val tx = mapper.map(validatedRow("지출"), userId = 1L)
        assertEquals("식비", tx.category)
        assertEquals("스타벅스", tx.description)
    }
}
