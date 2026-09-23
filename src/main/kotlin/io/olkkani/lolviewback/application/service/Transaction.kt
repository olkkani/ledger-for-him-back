package io.olkkani.lolviewback.domain.transaction

import java.math.BigDecimal
import java.time.OffsetDateTime

data class Transaction(
    val id: Long,
    val userId: Long,
    val amount: BigDecimal,
    val description: String,
    val occurredAt: OffsetDateTime,
    val category: String,
)
