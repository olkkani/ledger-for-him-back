package io.olkkani.lolviewback.domain.transaction

import java.math.BigDecimal
import java.time.LocalDate

sealed class RowValidationResult {
    data class Valid(val row: ValidatedRow) : RowValidationResult()
    data class Invalid(val rowIndex: Int, val reason: String) : RowValidationResult()
    data object Blank : RowValidationResult()
}

data class ValidatedRow(
    val rowIndex: Int,
    val date: LocalDate,
    val category: String,
    val description: String,
    val incomeOrExpense: String,
    val amount: BigDecimal,
)

private val VALID_DIRECTIONS = setOf("수입", "지출")

class RowValidator {
    fun validate(row: RawTransactionRow): RowValidationResult {
        val isEntirelyBlank = row.date == null &&
            row.category.isNullOrBlank() &&
            row.description.isNullOrBlank() &&
            row.incomeOrExpense.isNullOrBlank() &&
            row.amount.isNullOrBlank()
        if (isEntirelyBlank) return RowValidationResult.Blank

        if (row.date == null) {
            return RowValidationResult.Invalid(row.rowIndex, "날짜를 읽을 수 없습니다")
        }

        val direction = row.incomeOrExpense?.trim()
        if (direction !in VALID_DIRECTIONS) {
            return RowValidationResult.Invalid(row.rowIndex, "수입/지출 값이 올바르지 않습니다: ${row.incomeOrExpense}")
        }

        val cleanedAmount = row.amount?.replace(",", "")?.trim()
        val amount = cleanedAmount?.toBigDecimalOrNull()
        if (amount == null || amount <= BigDecimal.ZERO) {
            return RowValidationResult.Invalid(row.rowIndex, "금액이 올바르지 않습니다: ${row.amount}")
        }

        return RowValidationResult.Valid(
            ValidatedRow(
                rowIndex = row.rowIndex,
                date = row.date,
                category = row.category ?: "",
                description = row.description ?: "",
                incomeOrExpense = direction!!,
                amount = amount,
            ),
        )
    }
}
