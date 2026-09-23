package io.olkkani.lolviewback.application.service

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RowValidatorTest {

    private val validator = RowValidator()

    private fun rawRow(
        rowIndex: Int = 2,
        date: LocalDate? = LocalDate.of(2026, 1, 15),
        category: String? = "식비",
        description: String? = "스타벅스",
        incomeOrExpense: String? = "지출",
        amount: String? = "4500",
    ) = RawTransactionRow(rowIndex, date, category, description, incomeOrExpense, amount)

    @Test
    fun `valid expense row parses to ValidatedRow with positive amount`() {
        val result = validator.validate(rawRow())
        val valid = assertIs<RowValidationResult.Valid>(result)
        assertEquals(BigDecimal("4500"), valid.row.amount)
        assertEquals("지출", valid.row.incomeOrExpense)
    }

    @Test
    fun `trims whitespace around 수입지출 value`() {
        val result = validator.validate(rawRow(incomeOrExpense = " 지출 "))
        val valid = assertIs<RowValidationResult.Valid>(result)
        assertEquals("지출", valid.row.incomeOrExpense)
    }

    @Test
    fun `strips thousands separators from amount`() {
        val result = validator.validate(rawRow(amount = "1,234,500"))
        val valid = assertIs<RowValidationResult.Valid>(result)
        assertEquals(BigDecimal("1234500"), valid.row.amount)
    }

    @Test
    fun `zero amount is invalid`() {
        val result = validator.validate(rawRow(amount = "0"))
        assertIs<RowValidationResult.Invalid>(result)
    }

    @Test
    fun `negative amount is invalid`() {
        val result = validator.validate(rawRow(amount = "-500"))
        assertIs<RowValidationResult.Invalid>(result)
    }

    @Test
    fun `unparseable amount is invalid`() {
        val result = validator.validate(rawRow(amount = "abc"))
        assertIs<RowValidationResult.Invalid>(result)
    }

    @Test
    fun `incomeOrExpense not exactly 수입 or 지출 is invalid`() {
        val result = validator.validate(rawRow(incomeOrExpense = "이체"))
        assertIs<RowValidationResult.Invalid>(result)
    }

    @Test
    fun `null date is invalid`() {
        val result = validator.validate(rawRow(date = null))
        assertIs<RowValidationResult.Invalid>(result)
    }

    @Test
    fun `entirely blank row is Blank, not Invalid`() {
        val result = validator.validate(
            RawTransactionRow(
                rowIndex = 5,
                date = null,
                category = null,
                description = null,
                incomeOrExpense = null,
                amount = null,
            ),
        )
        assertEquals(RowValidationResult.Blank, result)
    }

    @Test
    fun `null category or description on an otherwise valid row defaults to empty string`() {
        val result = validator.validate(rawRow(category = null, description = null))
        val valid = assertIs<RowValidationResult.Valid>(result)
        assertEquals("", valid.row.category)
        assertEquals("", valid.row.description)
    }
}
