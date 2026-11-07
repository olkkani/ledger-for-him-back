package io.olkkani.lolviewback.adapter.inbound.web

import io.olkkani.lolviewback.adapter.config.StubPrincipalResolver
import io.olkkani.lolviewback.adapter.inbound.web.dto.CategoryRecommendationResponseDto
import io.olkkani.lolviewback.application.service.CategoryRecommendationService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.OffsetDateTime

@RestController
@RequestMapping("/transactions/recommendations")
class CategoryRecommendationController(
    private val service: CategoryRecommendationService,
    private val stubPrincipalResolver: StubPrincipalResolver,
) {
    @GetMapping("/category")
    fun recommend(
        @RequestParam(required = false) amount: BigDecimal?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) targetDateTime: OffsetDateTime?,
    ): List<CategoryRecommendationResponseDto> {
        val userId = stubPrincipalResolver.currentUserId()
        val resolvedTargetDateTime = targetDateTime ?: OffsetDateTime.now()

        return service.recommend(userId, amount, resolvedTargetDateTime)
            .map { CategoryRecommendationResponseDto(it.category, it.score, it.frequency) }
    }
}
