package com.ikk.backend.features.projects.api

import com.ikk.backend.features.projects.application.ProjectService
import com.ikk.backend.features.projects.domain.Project
import jakarta.validation.Valid
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/v1/projects")
class ProjectController(private val projects: ProjectService) {
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: CreateProjectRequest): ProjectResponse =
        projects.create(request.name).toResponse()

    @GetMapping("/{projectId}")
    fun get(@PathVariable projectId: String): ProjectResponse = projects.require(projectId).toResponse()
}

data class CreateProjectRequest(
    @field:NotBlank
    @field:Size(max = 200)
    val name: String,
)

data class ProjectResponse(
    val id: String,
    val name: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val latestCheckpoint: String,
)

private fun Project.toResponse() = ProjectResponse(id, name, createdAt, updatedAt, currentCheckpoint)
