package io.olkkani.lolviewback.domain.transaction

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Workbook
import org.springframework.stereotype.Component
import java.time.LocalDate

data class RawTransactionRow(
    val rowIndex: Int,
    val date: LocalDate?,
    val category: String?,
    val description: String?,
    val incomeOrExpense: String?,
    val amount: String?,
)

private const val COL_DATE = 0
private const val COL_CATEGORY = 2
private const val COL_DESCRIPTION = 4
private const val COL_INCOME_OR_EXPENSE = 6
private const val COL_AMOUNT = 8
private const val HEADER_ROW_INDEX = 0

@Component
class RowParser {
    private val formatter = DataFormatter()

    fun parse(workbook: Workbook): List<RawTransactionRow> {
        val sheet = workbook.getSheetAt(0)
        val result = mutableListOf<RawTransactionRow>()
        for (rowIdx in (HEADER_ROW_INDEX + 1)..sheet.lastRowNum) {
            val row = sheet.getRow(rowIdx) ?: continue
            result.add(
                RawTransactionRow(
                    rowIndex = rowIdx + 1,
                    date = dateCellValue(row, COL_DATE),
                    category = cellText(row, COL_CATEGORY),
                    description = cellText(row, COL_DESCRIPTION),
                    incomeOrExpense = cellText(row, COL_INCOME_OR_EXPENSE),
                    amount = cellText(row, COL_AMOUNT),
                ),
            )
        }
        return result
    }

    private fun cellText(row: Row, colIndex: Int): String? {
        val cell = row.getCell(colIndex) ?: return null
        if (cell.cellType == CellType.BLANK) return null
        return formatter.formatCellValue(cell).takeIf { it.isNotBlank() }
    }

    private fun dateCellValue(row: Row, colIndex: Int): LocalDate? {
        val cell = row.getCell(colIndex) ?: return null
        if (cell.cellType == CellType.BLANK) return null
        // DateUtil.isCellDateFormatted() throws on STRING cells, so check cell type first
        if (cell.cellType == CellType.STRING) return null
        if (!DateUtil.isCellDateFormatted(cell)) return null
        return cell.localDateTimeCellValue.toLocalDate()
    }
}
