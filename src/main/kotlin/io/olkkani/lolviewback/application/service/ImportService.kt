package io.olkkani.lolviewback.domain.transaction

import org.apache.poi.ss.usermodel.Workbook
import org.springframework.dao.DuplicateKeyException
import org.springframework.stereotype.Service

sealed class ImportResult {
    data class Success(val importedCount: Int) : ImportResult()
    data class ValidationFailure(val failures: List<RowFailure>) : ImportResult()
    data class DuplicateFailure(val message: String) : ImportResult()
}

data class RowFailure(val rowIndex: Int, val reason: String)

@Service
class ImportService(
    private val rowParser: RowParser,
    private val rowValidator: RowValidator,
    private val transactionMapper: TransactionMapper,
    private val transactionRepository: TransactionRepository,
) {
    fun import(workbook: Workbook, userId: Long): ImportResult {
        val rawRows = rowParser.parse(workbook)

        val validRows = mutableListOf<ValidatedRow>()
        val failures = mutableListOf<RowFailure>()
        for (raw in rawRows) {
            when (val outcome = rowValidator.validate(raw)) {
                is RowValidationResult.Valid -> validRows.add(outcome.row)
                is RowValidationResult.Invalid -> failures.add(RowFailure(outcome.rowIndex, outcome.reason))
                RowValidationResult.Blank -> Unit
            }
        }

        if (failures.isNotEmpty()) {
            return ImportResult.ValidationFailure(failures)
        }

        val transactions = validRows.map { transactionMapper.map(it, userId) }

        return try {
            transactionRepository.insertAll(transactions)
            ImportResult.Success(transactions.size)
        } catch (e: DuplicateKeyException) {
            ImportResult.DuplicateFailure("이미 가져온 거래와 중복되는 행이 있어 전체 가져오기를 취소했습니다.")
        }
    }
}
