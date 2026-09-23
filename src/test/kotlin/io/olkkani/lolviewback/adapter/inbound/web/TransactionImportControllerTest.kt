package io.olkkani.lolviewback.adapter.inbound.web

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.olkkani.lolviewback.application.service.ImportResult
import io.olkkani.lolviewback.application.service.ImportService
import io.olkkani.lolviewback.application.service.RowFailure
import io.olkkani.lolviewback.adapter.config.StubPrincipalResolver
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.io.ByteArrayOutputStream

@WebMvcTest(TransactionImportController::class)
class TransactionImportControllerTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var importService: ImportService

    @Autowired
    lateinit var stubPrincipalResolver: StubPrincipalResolver

    @TestConfiguration
    class MockBeansConfig {
        @Bean
        fun importService(): ImportService = mockk()

        @Bean
        fun stubPrincipalResolver(): StubPrincipalResolver = mockk()
    }

    @BeforeEach
    fun resetMocks() {
        clearMocks(importService, stubPrincipalResolver)
    }

    /**
     * MockMultipartFile with an empty byte array is rejected by POI's WorkbookFactory
     * with EmptyFileException before the mocked ImportService is ever reached, so tests
     * exercising the parse-then-delegate path need real (minimal) workbook bytes.
     */
    private fun minimalXlsxBytes(): ByteArray {
        XSSFWorkbook().use { workbook ->
            workbook.createSheet()
            val out = ByteArrayOutputStream()
            workbook.write(out)
            return out.toByteArray()
        }
    }

    @Test
    fun `valid xlsx upload returns 200 with imported count`() {
        every { stubPrincipalResolver.currentUserId() } returns 1L
        every { importService.import(any(), 1L) } returns ImportResult.Success(3)

        val file =
            MockMultipartFile(
                "file",
                "sample.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                minimalXlsxBytes(),
            )

        mockMvc
            .perform(multipart("/api/transactions/import").file(file))
            .andExpect(status().isOk)
    }

    @Test
    fun `validation failure returns 422`() {
        every { stubPrincipalResolver.currentUserId() } returns 1L
        every { importService.import(any(), 1L) } returns ImportResult.ValidationFailure(listOf(RowFailure(3, "금액이 올바르지 않습니다")))

        val file =
            MockMultipartFile(
                "file",
                "sample.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                minimalXlsxBytes(),
            )

        mockMvc
            .perform(multipart("/api/transactions/import").file(file))
            .andExpect(status().isUnprocessableEntity)
    }

    @Test
    fun `duplicate failure returns 409`() {
        every { stubPrincipalResolver.currentUserId() } returns 1L
        every { importService.import(any(), 1L) } returns ImportResult.DuplicateFailure("중복된 거래가 있습니다.")

        val file =
            MockMultipartFile(
                "file",
                "sample.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                minimalXlsxBytes(),
            )

        mockMvc
            .perform(multipart("/api/transactions/import").file(file))
            .andExpect(status().isConflict)
    }

    @Test
    fun `wrong file extension returns 400 without calling the import service`() {
        val file = MockMultipartFile("file", "sample.txt", "text/plain", ByteArray(0))

        mockMvc
            .perform(multipart("/api/transactions/import").file(file))
            .andExpect(status().isBadRequest)

        verify(exactly = 0) { importService.import(any(), any()) }
    }

    @Test
    fun `corrupt file with xlsx extension returns 400 without calling the import service`() {
        every { stubPrincipalResolver.currentUserId() } returns 1L

        val corruptBytes = "this is not a real xlsx file, just plain garbage bytes".toByteArray()
        val file =
            MockMultipartFile(
                "file",
                "sample.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                corruptBytes,
            )

        mockMvc
            .perform(multipart("/api/transactions/import").file(file))
            .andExpect(status().isBadRequest)

        verify(exactly = 0) { importService.import(any(), any()) }
    }
}
