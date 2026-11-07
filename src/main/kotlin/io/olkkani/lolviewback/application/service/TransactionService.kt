package io.olkkani.lolviewback.application.service

import io.olkkani.lolviewback.adapter.outbound.persistence.dao.TransactionDao
import io.olkkani.lolviewback.adapter.outbound.persistence.dao.TransactionRepository
import io.olkkani.lolviewback.adapter.outbound.persistence.entity.Transaction
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.YearMonth
import java.time.ZoneId

@Service
class TransactionService (
    private val transactionDao: TransactionDao,
    private val transactionRepository: TransactionRepository,
    ){

    /** Transactions of [userId] whose occurredAt falls in [yearMonth] (Asia/Seoul), newest first. */
    @Transactional(readOnly = true)
    fun getAllTransaction(userId: Long, yearMonth: YearMonth): List<Transaction> {
        val from = yearMonth.atDay(1).atStartOfDay(SEOUL).toOffsetDateTime()
        val to = yearMonth.plusMonths(1).atDay(1).atStartOfDay(SEOUL).toOffsetDateTime()
        return transactionRepository
            .findByUserIdAndOccurredAtGreaterThanEqualAndOccurredAtLessThanOrderByOccurredAtDescIdDesc(userId, from, to)
    }

    private companion object {
        val SEOUL: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
