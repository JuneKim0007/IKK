package com.ikk.backend.features.codegen.api

import com.ikk.backend.features.codegen.application.CodegenService
import com.ikk.backend.features.codegen.domain.Artifact
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/projects/{projectId}")
class CodegenController(private val codegen: CodegenService) {
    @PostMapping("/generate")
    fun generate(
        @PathVariable projectId: String,
        @RequestBody(required = false) request: GenerateRequest?,
    ): GenerateResponse {
        val result = codegen.generate(projectId, request?.targets)
        return GenerateResponse(result.checkpoint, result.artifacts.map(Artifact::toResponse))
    }

    @GetMapping("/artifacts/{name:.+}")
    fun artifact(@PathVariable projectId: String, @PathVariable name: String): ResponseEntity<String> {
        val artifact = codegen.requireArtifact(projectId, name)
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType(artifact.mime))
            .contentLength(artifact.bytes)
            .body(artifact.content)
    }
}

data class GenerateRequest(val targets: List<String> = emptyList())
data class ArtifactResponse(val name: String, val target: String, val bytes: Long)
data class GenerateResponse(val checkpoint: String, val artifacts: List<ArtifactResponse>)
private fun Artifact.toResponse() = ArtifactResponse(name, target, bytes)
