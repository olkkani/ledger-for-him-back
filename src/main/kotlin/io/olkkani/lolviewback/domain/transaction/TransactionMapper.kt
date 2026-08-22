package io.olkkani.lolviewback.domain.transaction

import io.hypersistence.tsid.TSID
import java.time.OffsetDateTime
import java.time.ZoneId

private val SEOUL = ZoneId.of("Asia/Seoul")
private const val EXPENSE_LABEL = "지출"

class TransactionMapper(
    private val idGenerator: () -> Long = { TSID.Factory.getTsid().toLong() },
) {
    fun map(row: ValidatedRow, userId: Long): Transaction {
        val signedAmount = if (row.incomeOrExpense == EXPENSE_LABEL) row.amount.negate() else row.amount
        val occurredAt: OffsetDateTime = row.date.atStartOfDay(SEOUL).toOffsetDateTime()
        return Transaction(
            id = idGenerator(),
            userId = userId,
            amount = signedAmount,
            description = row.description,
            occurredAt = occurredAt,
            category = row.category,
        )
    }
}
