package io.olkkani.lolviewback.application.service

import org.jooq.DSLContext
import org.jooq.generated.Tables.TRANSACTIONS
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.dao.DuplicateKeyException
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.math.BigDecimal
import java.time.OffsetDateTime
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

@Testcontainers
@SpringBootTest
class TransactionRepositoryIntegrationTest {

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        @JvmStatic
        @DynamicPropertySource
        fun props(registry: DynamicPropertyRegistry) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl)
            registry.add("spring.datasource.username", postgres::getUsername)
            registry.add("spring.datasource.password", postgres::getPassword)
        }
    }

    @Autowired
    lateinit var repository: TransactionRepository

    @Autowired
    lateinit var dsl: DSLContext

    @BeforeEach
    fun cleanTable() {
        dsl.deleteFrom(TRANSACTIONS).execute()
    }

    private fun sampleTx(id: Long, amount: BigDecimal = BigDecimal("-4500")) = Transaction(
        id = id,
        userId = 1L,
        amount = amount,
        description = "스타벅스",
        occurredAt = OffsetDateTime.parse("2026-01-15T00:00:00+09:00"),
        category = "식비",
    )

    @Test
    fun `insertAll persists every row in one call`() {
        repository.insertAll(listOf(sampleTx(id = 1L), sampleTx(id = 2L, amount = BigDecimal("3000000"))))

        val count = dsl.selectCount().from(TRANSACTIONS).where(TRANSACTIONS.USER_ID.eq(1L)).fetchOne(0, Int::class.java)
        assertEquals(2, count)
    }

    @Test
    fun `insertAll throws DuplicateKeyException on unique constraint collision`() {
        repository.insertAll(listOf(sampleTx(id = 1L)))

        assertFailsWith<DuplicateKeyException> {
            repository.insertAll(listOf(sampleTx(id = 2L))) // different id, same (user_id, occurred_at, description, amount)
        }
    }

    @Test
    fun `insertAll rolls back the whole batch when one row collides`() {
        repository.insertAll(listOf(sampleTx(id = 1L)))

        assertFailsWith<DuplicateKeyException> {
            repository.insertAll(
                listOf(
                    sampleTx(id = 3L, amount = BigDecimal("-9999")), // would succeed alone
                    sampleTx(id = 4L), // duplicate of id=1's row
                ),
            )
        }

        val count = dsl.selectCount().from(TRANSACTIONS).where(TRANSACTIONS.ID.eq(3L)).fetchOne(0, Int::class.java)
        assertEquals(0, count) // id=3 must NOT have persisted despite being valid on its own
    }
}
