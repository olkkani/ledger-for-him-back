package io.olkkani.lolviewback.adapter.inbound.web.dto

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Transaction
import java.math.BigDecimal
import java.time.OffsetDateTime

data class TransactionResponseDto(
    val id: Long,
    val amount: BigDecimal,
    val description: String,
    val memo: String?,
    val category: String?,
    val occurredAt: OffsetDateTime,
) {
    companion object {
        fun from(tx: Transaction) = TransactionResponseDto(
            id = tx.id,
            amount = tx.amount,
            description = tx.description,
            memo = tx.memo,
            category = tx.category,
            occurredAt = tx.occurredAt,
        )
    }
}
