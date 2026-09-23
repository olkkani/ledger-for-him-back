package io.olkkani.lolviewback.application.service

import org.jooq.DSLContext
import org.jooq.DatePart
import org.jooq.generated.Tables.TRANSACTIONS
import org.jooq.impl.DSL
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.LocalDateTime
import java.time.OffsetDateTime

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

    /**
     * TRIM(LOWER(category))-grouped aggregates for category recommendation scoring.
     * occurred_at < targetDateTime is a hard future-leak boundary (matches the sibling
     * description-recommendation design's history_strictly_before). Hour/day-of-week
     * are extracted in Asia/Seoul, never UTC or server default.
     */
    fun findCategoryAggregates(userId: Long, targetDateTime: OffsetDateTime): List<CategoryAggregate> {
        val normalizedCategory = DSL.trim(DSL.lower(TRANSACTIONS.CATEGORY))
        // No portable jOOQ DSL for Postgres' "AT TIME ZONE" — raw SQL is required here.
        val occurredAtSeoul = DSL.field(
            "{0} AT TIME ZONE 'Asia/Seoul'",
            LocalDateTime::class.java,
            TRANSACTIONS.OCCURRED_AT,
        )
        val hour = DSL.extract(occurredAtSeoul, DatePart.HOUR)
        // jOOQ's typed DatePart.DAY_OF_WEEK remaps to the ISO/SQL convention (1=Sunday..7=Saturday)
        // on Postgres, shifting every value by +1 from Postgres' native EXTRACT(DOW) (0=Sunday..6=Saturday).
        // CategoryRecommendationScorer (Task 1) hardcodes the native 0-6 convention, so we must bypass
        // jOOQ's DatePart here and use raw SQL to get Postgres' unshifted value.
        val dow = DSL.field(
            "extract(dow from {0})",
            Double::class.java,
            occurredAtSeoul,
        )
        // Circular mean (atan2/sin/cos) has no jOOQ typed equivalent — raw SQL is required here too.
        val hourAngle = DSL.field(
            "atan2(avg(sin(2 * pi() * {0} / 24)), avg(cos(2 * pi() * {0} / 24)))",
            Double::class.java,
            hour,
        )
        val dowAngle = DSL.field(
            "atan2(avg(sin(2 * pi() * {0} / 7)), avg(cos(2 * pi() * {0} / 7)))",
            Double::class.java,
            dow,
        )
        val mostRecentCategory = DSL.arrayAgg(TRANSACTIONS.CATEGORY).orderBy(TRANSACTIONS.OCCURRED_AT.desc())
        val avgAbsAmount = DSL.avg(DSL.abs(TRANSACTIONS.AMOUNT))
        val frequency = DSL.count()

        return dsl.select(
            normalizedCategory,
            mostRecentCategory,
            avgAbsAmount,
            hourAngle,
            dowAngle,
            frequency,
        )
            .from(TRANSACTIONS)
            .where(TRANSACTIONS.USER_ID.eq(userId))
            .and(TRANSACTIONS.OCCURRED_AT.lt(targetDateTime))
            .groupBy(normalizedCategory)
            .fetch { row ->
                CategoryAggregate(
                    category = row[mostRecentCategory].first(),
                    avgAmount = row[avgAbsAmount] ?: BigDecimal.ZERO,
                    typicalHourRadians = row[hourAngle] ?: 0.0,
                    typicalDowRadians = row[dowAngle] ?: 0.0,
                    frequency = row[frequency],
                )
            }
    }

    /** The category of the user's single most recent transaction strictly before targetDateTime, or null if none exists. */
    fun findMostRecentCategory(userId: Long, targetDateTime: OffsetDateTime): String? {
        return dsl.select(TRANSACTIONS.CATEGORY)
            .from(TRANSACTIONS)
            .where(TRANSACTIONS.USER_ID.eq(userId))
            .and(TRANSACTIONS.OCCURRED_AT.lt(targetDateTime))
            .orderBy(TRANSACTIONS.OCCURRED_AT.desc())
            .limit(1)
            .fetchOne(TRANSACTIONS.CATEGORY)
    }
}
