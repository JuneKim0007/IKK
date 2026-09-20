package com.ikk.backend.features.health

import com.ikk.backend.contract.BackendContract
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Readiness, not liveness.
 *
 * A check that only proves the process answers sockets reports "ok" while
 * every contract request fails — which is exactly how a broken deploy looks
 * healthy. This round-trips [ContractCanary] through the same decoder and
 * validator real requests use, so both ways the service actually breaks — the
 * database being unreachable, and the contract path rejecting valid input —
 * turn it red.
 *
 * Failing checks answer 503, because a load balancer that only reads the
 * status code must take this instance out of rotation.
 */
@RestController
class HealthController(private val jdbc: JdbcTemplate) {

    @GetMapping("/healthz")
    fun health(): ResponseEntity<Map<String, Any>> {
        val checks = linkedMapOf<String, Any>()
        var healthy = true

        runCatching { jdbc.queryForObject("SELECT 1", Int::class.java) }
            .onSuccess { checks["database"] = "ok" }
            .onFailure { healthy = false; checks["database"] = it.message ?: "unreachable" }

        runCatching {
            val contract = BackendContract.decode(ContractCanary.JSON)
            val violations = BackendContract.validate(contract)
            check(violations.isEmpty()) { "canary rejected: ${violations.joinToString()}" }
            check(contract.components.size == ContractCanary.NODE_COUNT) {
                "canary decoded ${contract.components.size} of ${ContractCanary.NODE_COUNT} nodes"
            }
        }
            .onSuccess { checks["contract"] = "ok" }
            .onFailure { healthy = false; checks["contract"] = it.message ?: "failed" }

        checks["status"] = if (healthy) "ok" else "degraded"
        return ResponseEntity
            .status(if (healthy) HttpStatus.OK else HttpStatus.SERVICE_UNAVAILABLE)
            .body(checks)
    }
}
