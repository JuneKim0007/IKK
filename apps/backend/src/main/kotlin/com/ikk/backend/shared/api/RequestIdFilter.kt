package com.ikk.backend.shared.api

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class RequestIdFilter : OncePerRequestFilter() {
    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val incoming = request.getHeader(HEADER)
        val requestId = incoming
            ?.takeIf { it.matches(Regex("[A-Za-z0-9._-]{1,100}")) }
            ?: "req_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        request.setAttribute(REQUEST_ID_ATTRIBUTE, requestId)
        response.setHeader(HEADER, requestId)
        MDC.put("requestId", requestId)
        try {
            filterChain.doFilter(request, response)
        } finally {
            MDC.remove("requestId")
        }
    }

    companion object {
        const val REQUEST_ID_ATTRIBUTE = "com.ikk.backend.requestId"
        private const val HEADER = "X-Request-ID"
    }
}
