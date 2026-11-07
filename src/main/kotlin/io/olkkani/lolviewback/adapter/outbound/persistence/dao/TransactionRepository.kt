package io.olkkani.lolviewback.adapter.outbound.persistence.dao

import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Transaction
import org.springframework.data.jpa.repository.JpaRepository
import java.time.OffsetDateTime

interface TransactionRepository: JpaRepository<Transaction, Long> {

    /** occurredAt in [from, to) for the user, newest first. */
    fun findByUserIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDescIdDesc(
        userId: Long,
        from: OffsetDateTime,
        to: OffsetDateTime,
    ): List<Transaction>
}