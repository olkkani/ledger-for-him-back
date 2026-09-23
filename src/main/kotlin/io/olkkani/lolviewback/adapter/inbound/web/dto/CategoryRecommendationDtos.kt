package io.olkkani.lolviewback.adapter.inbound.web.dto

data class CategoryRecommendationResponseDto(
    val category: String,
    val score: Double,
    val frequency: Int,
)
