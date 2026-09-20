package com.ikk.backend.features.health

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class HealthController(private val jdbc: JdbcTemplate) {
    @GetMapping("/healthz")
    fun health(): Map<String, String> {
        jdbc.queryForObject("SELECT 1", Int::class.java)
        return mapOf("status" to "ok")
    }
}
