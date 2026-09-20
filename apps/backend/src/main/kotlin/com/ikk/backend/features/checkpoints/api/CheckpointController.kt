package com.ikk.backend.features.checkpoints.api

import com.ikk.backend.features.checkpoints.application.CheckpointService
import com.ikk.backend.features.checkpoints.domain.Checkpoint
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import tools.jackson.databind.JsonNode
import java.time.Instant

@RestController
@RequestMapping("/v1/projects/{projectId}/checkpoints")
class CheckpointController(private val checkpoints: CheckpointService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@PathVariable projectId: String): CheckpointResponse =
        checkpoints.create(projectId).toResponse()

    @GetMapping
    fun list(@PathVariable projectId: String): CheckpointListResponse =
        CheckpointListResponse(checkpoints.list(projectId).map(Checkpoint::toResponse))

    @GetMapping("/{checkpoint}")
    fun get(@PathVariable projectId: String, @PathVariable checkpoint: String): JsonNode =
        checkpoints.get(projectId, checkpoint)
}

data class CheckpointResponse(val checkpoint: String, val createdAt: Instant)
data class CheckpointListResponse(val checkpoints: List<CheckpointResponse>)
private fun Checkpoint.toResponse() = CheckpointResponse(checkpoint, createdAt)
