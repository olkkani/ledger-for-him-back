package io.olkkani.lolviewback.adapter.config.security

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * 인증 체계(JWT/OAuth2)가 아직 없으므로 모든 요청을 허용한다.
 * 인증 도입 시 `anyRequest().permitAll()` 을 URL별 규칙 + `authenticated()` 로 교체할 것.
 * (함께 [io.olkkani.lolviewback.adapter.config.StubPrincipalResolver] 도 교체)
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig(
    @Value("\${app.cors.allowed-origins:}") private val allowedOrigins: List<String>,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain =
        http
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            // 토큰 기반 stateless API: 세션/쿠키 인증이 없으므로 CSRF 불필요
            .csrf { it.disable() }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            // 기본 로그인 페이지/Basic 인증/로그아웃 페이지 비활성화
            .formLogin { it.disable() }
            .httpBasic { it.disable() }
            .logout { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .headers { headers ->
                headers.frameOptions { it.deny() }
                headers.contentTypeOptions { }
                // JSON API: 어떤 리소스도 로드/임베드되지 않도록 제한 (HSTS는 HTTPS 요청에 기본 적용)
                headers.contentSecurityPolicy { it.policyDirectives("default-src 'none'; frame-ancestors 'none'") }
                headers.referrerPolicy { it.policy(ReferrerPolicy.NO_REFERRER) }
            }.build()

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        require("*" !in allowedOrigins) { "app.cors.allowed-origins must list explicit origins, not '*'" }
        val configuration =
            CorsConfiguration().apply {
                allowedOrigins = this@SecurityConfig.allowedOrigins
                allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                allowedHeaders = listOf("*")
                // 쿠키 인증을 쓰지 않는 동안은 자격 증명 공유 불필요
                allowCredentials = false
                maxAge = 3600
            }
        return UrlBasedCorsConfigurationSource().apply { registerCorsConfiguration("/**", configuration) }
    }
}
