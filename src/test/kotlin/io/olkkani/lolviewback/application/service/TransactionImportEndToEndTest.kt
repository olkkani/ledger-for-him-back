package io.olkkani.lolviewback.application.service

import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.jooq.DSLContext
import org.jooq.generated.Tables.TRANSACTIONS
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import kotlin.test.assertEquals

@Testcontainers
@SpringBootTest
class TransactionImportEndToEndTest {

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
    lateinit var importService: ImportService

    @Autowired
    lateinit var dsl: DSLContext

    /**
     * Builds an in-memory .xlsx workbook matching the real source spreadsheet's
     * 11-column layout: 날짜/자산/분류/소분류/내용/KRW/수입지출/메모/금액/환율/자산.
     * Only the columns RowParser actually reads (0, 2, 4, 6, 8) are populated;
     * the rest are present as headers only, matching production data shape.
     */
    private fun buildFixtureWorkbook(): InputStream {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("data")
        val headerRow = sheet.createRow(0)
        listOf("날짜", "자산", "분류", "소분류", "내용", "KRW", "수입지출", "메모", "금액", "환율", "자산")
            .forEachIndexed { i, h -> headerRow.createCell(i).setCellValue(h) }

        fun dataRow(idx: Int, dateStr: String, category: String, desc: String, direction: String, amount: String) {
            val row = sheet.createRow(idx)
            val dateCellStyle = wb.createCellStyle().apply {
                dataFormat = wb.creationHelper.createDataFormat().getFormat("yyyy-mm-dd")
            }
            val parts = dateStr.split("-").map { it.toInt() }
            val dateCell = row.createCell(0)
            dateCell.cellStyle = dateCellStyle
            dateCell.setCellValue(
                Date.from(
                    LocalDate.of(parts[0], parts[1], parts[2])
                        .atStartOfDay(ZoneId.systemDefault()).toInstant(),
                ),
            )
            row.createCell(2).setCellValue(category)
            row.createCell(4).setCellValue(desc)
            row.createCell(6).setCellValue(direction)
            row.createCell(8).setCellValue(amount)
        }

        dataRow(1, "2026-01-15", "식비", "스타벅스", "지출", "4500")
        dataRow(2, "2026-01-25", "급여", "월급", "수입", "3000000")
        // blank row at index 3, intentionally left empty - must be skipped, not counted or failed

        val out = ByteArrayOutputStream()
        wb.write(out)
        wb.close()
        return ByteArrayInputStream(out.toByteArray())
    }

    @Test
    fun `full import persists signed amounts and skips the blank row`() {
        val workbook = WorkbookFactory.create(buildFixtureWorkbook())

        val result = importService.import(workbook, userId = 99L)

        val success = result as ImportResult.Success
        assertEquals(2, success.importedCount)

        val rows = dsl.selectFrom(TRANSACTIONS).where(TRANSACTIONS.USER_ID.eq(99L)).fetch()
        assertEquals(2, rows.size)

        val expenseRow = rows.first { it.get(TRANSACTIONS.DESCRIPTION) == "스타벅스" }
        assertEquals(BigDecimal("-4500.00"), expenseRow.get(TRANSACTIONS.AMOUNT))

        val incomeRow = rows.first { it.get(TRANSACTIONS.DESCRIPTION) == "월급" }
        assertEquals(BigDecimal("3000000.00"), incomeRow.get(TRANSACTIONS.AMOUNT))
    }
}
