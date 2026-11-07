package io.olkkani.lolviewback.adapter.inbound.web

import io.olkkani.lolviewback.adapter.config.StubPrincipalResolver
import io.olkkani.lolviewback.adapter.inbound.web.dto.TransactionResponseDto
import io.olkkani.lolviewback.application.service.TransactionService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.YearMonth

@RestController
@RequestMapping("/transactions")
class TransactionRestController(
    private val transactionService: TransactionService,
    private val stubPrincipalResolver: StubPrincipalResolver,
) {

    /** GET /transactions?yearMonth=2025-03 */
    @GetMapping
    fun getTransactions(
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM") yearMonth: YearMonth,
    ): List<TransactionResponseDto> {
        val userId = stubPrincipalResolver.currentUserId()
        return transactionService.getAllTransaction(userId, yearMonth)
            .map(TransactionResponseDto::from)
    }
}
