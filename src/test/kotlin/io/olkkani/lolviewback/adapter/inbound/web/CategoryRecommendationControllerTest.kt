package io.olkkani.lolviewback.adapter.inbound.web

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.olkkani.lolviewback.adapter.config.StubPrincipalResolver
import io.olkkani.lolviewback.application.service.CategoryCandidate
import io.olkkani.lolviewback.application.service.CategoryRecommendationService
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.math.BigDecimal
import java.time.OffsetDateTime

@WebMvcTest(CategoryRecommendationController::class)
class CategoryRecommendationControllerTest {

    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var service: CategoryRecommendationService

    @Autowired
    lateinit var stubPrincipalResolver: StubPrincipalResolver

    @TestConfiguration
    class MockBeansConfig {
        @Bean
        fun service(): CategoryRecommendationService = mockk()

        @Bean
        fun stubPrincipalResolver(): StubPrincipalResolver = mockk()
    }

    @BeforeEach
    fun resetMocks() {
        clearMocks(service, stubPrincipalResolver)
    }

    @Test
    fun `user_id comes from the authenticated principal, not a request parameter`() {
        every { stubPrincipalResolver.currentUserId() } returns StubPrincipalResolver.STUB_USER_ID
        every { service.recommend(any(), isNull(), any()) } returns emptyList()

        mockMvc.perform(get("/transactions/recommendations/category"))
            .andExpect(status().isOk)

        verify { service.recommend(StubPrincipalResolver.STUB_USER_ID, null, any()) }
    }

    @Test
    fun `targetDateTime omitted defaults to now`() {
        every { stubPrincipalResolver.currentUserId() } returns StubPrincipalResolver.STUB_USER_ID
        val captured = slot<OffsetDateTime>()
        every { service.recommend(any(), isNull(), capture(captured)) } returns emptyList()

        val before = OffsetDateTime.now()
        mockMvc.perform(get("/transactions/recommendations/category")).andExpect(status().isOk)
        val after = OffsetDateTime.now()

        assertTrue(!captured.captured.isBefore(before) && !captured.captured.isAfter(after))
    }

    @Test
    fun `targetDateTime provided is passed through unchanged`() {
        every { stubPrincipalResolver.currentUserId() } returns StubPrincipalResolver.STUB_USER_ID
        every { service.recommend(any(), isNull(), any()) } returns emptyList()

        mockMvc.perform(get("/transactions/recommendations/category?targetDateTime=2026-03-01T10:00:00+09:00"))
            .andExpect(status().isOk)

        verify { service.recommend(any(), isNull(), OffsetDateTime.parse("2026-03-01T10:00:00+09:00")) }
    }

    @Test
    fun `amount present triggers stage 2 by passing amount through to the service`() {
        every { stubPrincipalResolver.currentUserId() } returns StubPrincipalResolver.STUB_USER_ID
        every { service.recommend(any(), eq(BigDecimal("15000")), any()) } returns emptyList()

        mockMvc.perform(get("/transactions/recommendations/category?amount=15000"))
            .andExpect(status().isOk)

        verify { service.recommend(any(), BigDecimal("15000"), any()) }
    }

    @Test
    fun `amount omitted triggers stage 1 by passing null amount to the service`() {
        every { stubPrincipalResolver.currentUserId() } returns StubPrincipalResolver.STUB_USER_ID
        every { service.recommend(any(), isNull(), any()) } returns emptyList()

        mockMvc.perform(get("/transactions/recommendations/category"))
            .andExpect(status().isOk)

        verify { service.recommend(any(), null, any()) }
    }

    @Test
    fun `response body serializes candidates as category score frequency`() {
        every { stubPrincipalResolver.currentUserId() } returns StubPrincipalResolver.STUB_USER_ID
        every { service.recommend(any(), isNull(), any()) } returns listOf(
            CategoryCandidate(category = "식비", score = 0.8, frequency = 12),
        )

        mockMvc.perform(get("/transactions/recommendations/category"))
            .andExpect(status().isOk)
            .andExpect(content().json("""[{"category":"식비","score":0.8,"frequency":12}]"""))
    }
}
