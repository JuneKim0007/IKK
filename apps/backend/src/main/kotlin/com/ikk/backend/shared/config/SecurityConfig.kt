package com.ikk.backend.shared.config

import com.ikk.backend.shared.api.ApiError
import com.ikk.backend.shared.api.RequestIdFilter
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.FilterRegistrationBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.MediaType
import org.springframework.security.config.Customizer.withDefaults
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.stereotype.Component
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.UrlBasedCorsConfigurationSource
import org.springframework.web.filter.OncePerRequestFilter
import tools.jackson.databind.json.JsonMapper

@Configuration
class SecurityConfig(
    @Value("\${ikk.allowed-origins}") private val allowedOrigins: String,
) {
    @Bean
    fun securityFilterChain(http: HttpSecurity, apiTokenFilter: ApiTokenFilter): SecurityFilterChain =
        http
            .csrf { it.disable() }
            .cors(withDefaults())
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { it.anyRequest().permitAll() }
            .addFilterBefore(apiTokenFilter, UsernamePasswordAuthenticationFilter::class.java)
            .build()

    @Bean
    fun apiTokenFilterRegistration(filter: ApiTokenFilter) =
        FilterRegistrationBean(filter).apply { isEnabled = false }

    @Bean
    fun corsConfigurationSource() = UrlBasedCorsConfigurationSource().apply {
        registerCorsConfiguration("/**", CorsConfiguration().apply {
            allowedOrigins = this@SecurityConfig.allowedOrigins.split(',').map(String::trim).filter(String::isNotEmpty)
            allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "OPTIONS")
            allowedHeaders = listOf("*")
            exposedHeaders = listOf("X-Request-ID")
        })
    }
}

@Component
class ApiTokenFilter(
    @Value("\${ikk.api-token:}") private val token: String,
    private val jsonMapper: JsonMapper,
) : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        if (token.isBlank() || request.requestURI == "/healthz") {
            filterChain.doFilter(request, response)
            return
        }
        if (request.getHeader("Authorization") == "Bearer $token") {
            filterChain.doFilter(request, response)
            return
        }
        response.status = HttpServletResponse.SC_UNAUTHORIZED
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        jsonMapper.writeValue(
            response.outputStream,
            ApiError(
                "unauthorized",
                "missing or invalid bearer token",
                request.getAttribute(RequestIdFilter.REQUEST_ID_ATTRIBUTE)?.toString() ?: "unknown",
            ),
        )
    }
}
