package io.olkkani.lolviewback.domain.transaction

import org.jooq.DSLContext
import org.jooq.generated.Tables.TRANSACTIONS
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class TransactionRepository(private val dsl: DSLContext) {

    @Transactional
    fun insertAll(transactions: List<Transaction>) {
        val inserts = transactions.map { tx ->
            dsl.insertInto(TRANSACTIONS)
                .set(TRANSACTIONS.ID, tx.id)
                .set(TRANSACTIONS.USER_ID, tx.userId)
                .set(TRANSACTIONS.AMOUNT, tx.amount)
                .set(TRANSACTIONS.DESCRIPTION, tx.description)
                .set(TRANSACTIONS.OCCURRED_AT, tx.occurredAt)
                .set(TRANSACTIONS.CATEGORY, tx.category)
        }
        dsl.batch(inserts).execute()
    }
}
