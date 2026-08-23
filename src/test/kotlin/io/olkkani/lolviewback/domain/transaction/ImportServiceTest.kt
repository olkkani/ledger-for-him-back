package io.olkkani.lolviewback.domain.transaction

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Test
import org.springframework.dao.DuplicateKeyException
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertIs

class ImportServiceTest {

    private fun validatedRow(rowIndex: Int) = ValidatedRow(
        rowIndex = rowIndex,
        date = LocalDate.of(2026, 1, 15),
        category = "식비",
        description = "스타벅스",
        incomeOrExpense = "지출",
        amount = BigDecimal("4500"),
    )

    private fun sampleTx(id: Long) = Transaction(
        id = id,
        userId = 1L,
        amount = BigDecimal("-4500"),
        description = "스타벅스",
        occurredAt = java.time.OffsetDateTime.now(),
        category = "식비",
    )

    @Test
    fun `all valid rows insert once and return Success`() {
        val rawRows = listOf(RawTransactionRow(2, LocalDate.of(2026, 1, 15), "식비", "스타벅스", "지출", "4500"))
        val rowParser = mockk<RowParser> { every { parse(any()) } returns rawRows }
        val rowValidator = mockk<RowValidator> { every { validate(rawRows[0]) } returns RowValidationResult.Valid(validatedRow(2)) }
        val mapper = mockk<TransactionMapper> { every { map(any(), 1L) } returns sampleTx(1L) }
        val repository = mockk<TransactionRepository>(relaxed = true)

        val service = ImportService(rowParser, rowValidator, mapper, repository)
        val result = service.import(XSSFWorkbook(), userId = 1L)

        assertIs<ImportResult.Success>(result)
        assertEquals(1, result.importedCount)
        verify(exactly = 1) { repository.insertAll(any()) }
    }

    @Test
    fun `any invalid row rejects the whole import without calling the repository`() {
        val rawRows = listOf(
            RawTransactionRow(2, LocalDate.of(2026, 1, 15), "식비", "스타벅스", "지출", "4500"),
            RawTransactionRow(3, null, null, null, "이체", "abc"),
        )
        val rowParser = mockk<RowParser> { every { parse(any()) } returns rawRows }
        val rowValidator = mockk<RowValidator> {
            every { validate(rawRows[0]) } returns RowValidationResult.Valid(validatedRow(2))
            every { validate(rawRows[1]) } returns RowValidationResult.Invalid(3, "수입/지출 값이 올바르지 않습니다: 이체")
        }
        val mapper = mockk<TransactionMapper>()
        val repository = mockk<TransactionRepository>(relaxed = true)

        val service = ImportService(rowParser, rowValidator, mapper, repository)
        val result = service.import(XSSFWorkbook(), userId = 1L)

        val failure = assertIs<ImportResult.ValidationFailure>(result)
        assertEquals(1, failure.failures.size)
        assertEquals(3, failure.failures[0].rowIndex)
        verify(exactly = 0) { repository.insertAll(any()) }
    }

    @Test
    fun `blank rows are skipped and do not appear in failures`() {
        val rawRows = listOf(
            RawTransactionRow(2, LocalDate.of(2026, 1, 15), "식비", "스타벅스", "지출", "4500"),
            RawTransactionRow(3, null, null, null, null, null),
        )
        val rowParser = mockk<RowParser> { every { parse(any()) } returns rawRows }
        val rowValidator = mockk<RowValidator> {
            every { validate(rawRows[0]) } returns RowValidationResult.Valid(validatedRow(2))
            every { validate(rawRows[1]) } returns RowValidationResult.Blank
        }
        val mapper = mockk<TransactionMapper> { every { map(any(), 1L) } returns sampleTx(1L) }
        val repository = mockk<TransactionRepository>(relaxed = true)

        val service = ImportService(rowParser, rowValidator, mapper, repository)
        val result = service.import(XSSFWorkbook(), userId = 1L)

        assertIs<ImportResult.Success>(result)
        assertEquals(1, (result as ImportResult.Success).importedCount)
    }

    @Test
    fun `duplicate constraint violation translates to DuplicateFailure with a readable message`() {
        val rawRows = listOf(RawTransactionRow(2, LocalDate.of(2026, 1, 15), "식비", "스타벅스", "지출", "4500"))
        val rowParser = mockk<RowParser> { every { parse(any()) } returns rawRows }
        val rowValidator = mockk<RowValidator> { every { validate(rawRows[0]) } returns RowValidationResult.Valid(validatedRow(2)) }
        val mapper = mockk<TransactionMapper> { every { map(any(), 1L) } returns sampleTx(1L) }
        val repository = mockk<TransactionRepository> {
            every { insertAll(any()) } throws DuplicateKeyException("duplicate key value violates unique constraint")
        }

        val service = ImportService(rowParser, rowValidator, mapper, repository)
        val result = service.import(XSSFWorkbook(), userId = 1L)

        val failure = assertIs<ImportResult.DuplicateFailure>(result)
        assertEquals(false, failure.message.contains("constraint"), "message must be human-readable, not the raw DB error text")
    }
}
