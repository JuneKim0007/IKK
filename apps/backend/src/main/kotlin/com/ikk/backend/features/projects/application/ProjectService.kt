package com.ikk.backend.features.projects.application

import com.ikk.backend.features.projects.domain.Project
import com.ikk.backend.features.projects.infrastructure.ProjectRepository
import com.ikk.backend.shared.api.ApiException
import com.ikk.backend.shared.ids.IdGenerator
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import tools.jackson.databind.JsonNode
import java.time.Clock

@Service
class ProjectService(
    private val projects: ProjectRepository,
    private val ids: IdGenerator,
    private val clock: Clock = Clock.systemUTC(),
) {
    fun create(name: String): Project {
        val now = clock.instant()
        return Project(
            id = ids.next("p_"),
            name = name.trim(),
            screen = name.toScreenName(),
            schemaVersion = 1,
            currentCheckpoint = "cp_000",
            background = null,
            referenceWidth = 375,
            referenceHeight = 667,
            referenceUnit = "dp",
            layout = "relative",
            createdAt = now,
            updatedAt = now,
        ).also(projects::insert)
    }

    fun ensureForImport(id: String, contract: JsonNode): Project {
        if (!PROJECT_ID.matches(id)) {
            throw ApiException(
                HttpStatus.BAD_REQUEST,
                "invalid_project_id",
                "project id must contain 1-64 letters, digits, dots, underscores, or hyphens",
            )
        }
        projects.findForUpdate(id)?.let { return it }

        val now = clock.instant()
        val reference = contract.path("reference")
        return Project(
            id = id,
            name = contract.path("screen").stringValue(),
            screen = contract.path("screen").stringValue(),
            schemaVersion = contract.path("schemaVersion").intValue(),
            currentCheckpoint = contract.path("checkpoint").stringValue(),
            referenceWidth = reference.path("w").intValue(),
            referenceHeight = reference.path("h").intValue(),
            referenceUnit = reference.path("unit").stringValue(),
            layout = contract.path("layout").stringValue(),
            background = contract.get("background")?.takeUnless { it.isNull }?.toString(),
            createdAt = now,
            updatedAt = now,
        ).also(projects::insert)
    }

    fun require(id: String): Project = projects.find(id) ?: throw ApiException(
        HttpStatus.NOT_FOUND,
        "project_not_found",
        "project $id was not found",
    )

    fun requireForUpdate(id: String): Project = projects.findForUpdate(id) ?: throw ApiException(
        HttpStatus.NOT_FOUND,
        "project_not_found",
        "project $id was not found",
    )

    fun touch(id: String) {
        projects.touch(id, clock.instant())
    }

    private fun String.toScreenName(): String {
        val joined = trim()
            .split(Regex("[^A-Za-z0-9]+"))
            .filter(String::isNotBlank)
            .joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
        return when {
            joined.isBlank() -> "Screen"
            joined.first().isJavaIdentifierStart() -> joined
            else -> "Screen$joined"
        }
    }

    private companion object {
        val PROJECT_ID = Regex("[A-Za-z0-9._-]{1,64}")
    }
}
