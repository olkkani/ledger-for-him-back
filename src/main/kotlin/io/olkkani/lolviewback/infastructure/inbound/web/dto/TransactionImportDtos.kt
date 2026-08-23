package io.olkkani.lolviewback.infastructure.inbound.web.dto

data class ImportSuccessResponse(
    val importedCount: Int,
)

data class ImportRowFailureDto(
    val rowIndex: Int,
    val reason: String,
)

data class ImportValidationFailureResponse(
    val failures: List<ImportRowFailureDto>,
)

data class ImportDuplicateFailureResponse(
    val message: String,
)

data class ImportBadRequestResponse(
    val message: String,
)
