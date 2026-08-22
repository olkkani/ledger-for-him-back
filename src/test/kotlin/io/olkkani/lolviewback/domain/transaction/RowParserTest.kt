package io.olkkani.lolviewback.domain.transaction

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class RowParserTest {

    private fun buildWorkbook(rows: List<List<String?>>): XSSFWorkbook {
        val wb = XSSFWorkbook()
        val sheet = wb.createSheet("data")
        rows.forEachIndexed { rowIdx, cells ->
            val row = sheet.createRow(rowIdx)
            cells.forEachIndexed { colIdx, value ->
                if (value != null) {
                    row.createCell(colIdx, org.apache.poi.ss.usermodel.CellType.STRING)
                        .setCellValue(value)
                }
            }
        }
        return wb
    }

    @Test
    fun `extracts the five needed columns by fixed position, ignoring the rest`() {
        val header = listOf("날짜", "자산", "분류", "소분류", "내용", "KRW", "수입/지출", "메모", "금액", "환율", "자산")
        val dataRow = listOf(
            "2026-01-15", "국민은행", "식비", "카페", "스타벅스",
            "", "지출", "", "4500", "", "국민은행",
        )
        val wb = buildWorkbook(listOf(header, dataRow))

        val result = RowParser().parse(wb)

        assertEquals(1, result.size)
        val row = result[0]
        assertEquals(2, row.rowIndex) // 1-based, header is row 1, this is row 2
        assertEquals("2026-01-15", row.date)
        assertEquals("식비", row.category)
        assertEquals("스타벅스", row.description)
        assertEquals("지출", row.incomeOrExpense)
        assertEquals("4500", row.amount)
    }

    @Test
    fun `skips the header row`() {
        val header = listOf("날짜", "자산", "분류", "소분류", "내용", "KRW", "수입/지출", "메모", "금액", "환율", "자산")
        val wb = buildWorkbook(listOf(header))

        val result = RowParser().parse(wb)

        assertEquals(0, result.size)
    }

    @Test
    fun `blank cell in an unread column does not affect extraction`() {
        val header = listOf("날짜", "자산", "분류", "소분류", "내용", "KRW", "수입/지출", "메모", "금액", "환율", "자산")
        val dataRow = listOf("2026-01-15", null, "식비", null, "스타벅스", null, "지출", null, "4500", null, null)
        val wb = buildWorkbook(listOf(header, dataRow))

        val result = RowParser().parse(wb)

        assertEquals(1, result.size)
        assertNotNull(result[0].date) // date is present; sanity check other columns didn't shift
        assertEquals("2026-01-15", result[0].date)
    }
}
