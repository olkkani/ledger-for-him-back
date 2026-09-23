package io.olkkani.lolviewback.application.service

import org.apache.poi.ss.usermodel.WorkbookFactory
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.test.assertEquals

class PoiSmokeTest {
    @Test
    fun `xlsx workbook round-trips through WorkbookFactory`() {
        val bytes = ByteArrayOutputStream().use { out ->
            XSSFWorkbook().use { wb ->
                val sheet = wb.createSheet("data")
                val header = sheet.createRow(0)
                header.createCell(0).setCellValue("날짜")
                header.createCell(1).setCellValue("금액")
                wb.write(out)
            }
            out.toByteArray()
        }

        WorkbookFactory.create(ByteArrayInputStream(bytes)).use { wb ->
            val sheet = wb.getSheetAt(0)
            assertEquals("날짜", sheet.getRow(0).getCell(0).stringCellValue)
            assertEquals("금액", sheet.getRow(0).getCell(1).stringCellValue)
        }
    }
}
