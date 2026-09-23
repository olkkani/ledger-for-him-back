package io.olkkani.lolviewback.adapter.inbound.web

import io.olkkani.lolviewback.application.service.ImportResult
import io.olkkani.lolviewback.application.service.ImportService
import io.olkkani.lolviewback.adapter.config.StubPrincipalResolver
import io.olkkani.lolviewback.adapter.inbound.web.dto.ImportBadRequestResponse
import io.olkkani.lolviewback.adapter.inbound.web.dto.ImportDuplicateFailureResponse
import io.olkkani.lolviewback.adapter.inbound.web.dto.ImportRowFailureDto
import io.olkkani.lolviewback.adapter.inbound.web.dto.ImportSuccessResponse
import io.olkkani.lolviewback.adapter.inbound.web.dto.ImportValidationFailureResponse
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

private val ALLOWED_EXTENSIONS = setOf("xls", "xlsx")

@RestController
@RequestMapping("/api/transactions")
class TransactionImportController(
    private val importService: ImportService,
    private val stubPrincipalResolver: StubPrincipalResolver,
) {
    @PostMapping("/import")
    fun import(
        @RequestParam("file") file: MultipartFile,
    ): ResponseEntity<Any> {
        val extension = file.originalFilename?.substringAfterLast('.', "")?.lowercase()
        if (extension !in ALLOWED_EXTENSIONS) {
            return ResponseEntity.badRequest().body(ImportBadRequestResponse(".xls 또는 .xlsx 파일만 업로드할 수 있습니다."))
        }

        val userId = stubPrincipalResolver.currentUserId()
        val workbook =
            try {
                file.inputStream.use { WorkbookFactory.create(it) }
            } catch (e: Exception) {
                return ResponseEntity.badRequest().body(ImportBadRequestResponse("엑셀 파일을 읽을 수 없습니다."))
            }

        return workbook.use { wb ->
            when (val result = importService.import(wb, userId)) {
                is ImportResult.Success ->
                    ResponseEntity.ok(ImportSuccessResponse(result.importedCount))
                is ImportResult.ValidationFailure ->
                    ResponseEntity.unprocessableEntity().body(
                        ImportValidationFailureResponse(result.failures.map { ImportRowFailureDto(it.rowIndex, it.reason) }),
                    )
                is ImportResult.DuplicateFailure ->
                    ResponseEntity.status(409).body(ImportDuplicateFailureResponse(result.message))
            }
        }
    }
}
