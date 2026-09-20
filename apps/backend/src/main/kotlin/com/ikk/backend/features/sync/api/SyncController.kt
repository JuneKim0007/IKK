package com.ikk.backend.features.sync.api

import com.ikk.backend.features.sync.application.SyncService
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.Positive
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import java.time.Instant

@RestController
@RequestMapping("/v1/projects/{projectId}/nodes")
class SyncController(private val sync: SyncService) {
    @PutMapping
    fun upsert(
        @PathVariable projectId: String,
        @Valid @RequestBody request: BatchUpsertRequest,
    ): BatchUpsertResponse = sync.upsert(projectId, request)

    @DeleteMapping("/{nodeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable projectId: String, @PathVariable nodeId: String) {
        sync.delete(projectId, nodeId)
    }
}

data class BatchUpsertRequest(
    @field:NotNull
    @field:Valid
    val nodes: List<NodeEnvelope>,
)

data class NodeEnvelope(
    @field:NotBlank val id: String,
    @field:NotBlank val type: String,
    @field:Positive val schemaVersion: Int,
    @field:Positive val version: Int,
    @field:NotNull val updatedAt: Instant,
    @field:NotNull val payload: JsonNode,
)

data class RejectedNode(val id: String, val reason: String, val server: JsonNode)

data class BatchUpsertResponse(
    val accepted: List<String>,
    val rejected: List<RejectedNode>,
    val checksums: Map<String, String>,
)
