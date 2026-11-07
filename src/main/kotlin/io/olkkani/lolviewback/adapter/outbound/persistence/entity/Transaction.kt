package io.olkkani.lolviewback.adapter.outbound.persistence.entity

import io.hypersistence.tsid.TSID
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.OffsetDateTime

// Schema (constraints, indexes, created_at default) is owned by Flyway; ddl-auto=validate guards drift.
@Entity
@Table(name = "transactions")
class Transaction(
    @Id
    val id: Long = TSID.Factory.getTsid().toLong(),
    val userId: Long,
    var amount: BigDecimal,
    var description: String,
    var memo: String? = null,
    var category: String? = null,
    var occurredAt: OffsetDateTime,
)