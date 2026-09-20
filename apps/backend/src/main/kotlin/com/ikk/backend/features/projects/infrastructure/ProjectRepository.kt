package com.ikk.backend.features.projects.infrastructure

import com.ikk.backend.features.projects.domain.Project
import com.ikk.backend.shared.persistence.toJdbcTime
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.sql.ResultSet
import java.time.Instant
import java.time.OffsetDateTime

@Repository
class ProjectRepository(private val jdbc: JdbcTemplate) {
    fun insert(project: Project) {
        jdbc.update(
            """
            INSERT INTO projects (
                id, name, screen, schema_version, current_checkpoint,
                reference_w, reference_h, reference_unit, layout, created_at, updated_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            project.id,
            project.name,
            project.screen,
            project.schemaVersion,
            project.currentCheckpoint,
            project.referenceWidth,
            project.referenceHeight,
            project.referenceUnit,
            project.layout,
            project.createdAt.toJdbcTime(),
            project.updatedAt.toJdbcTime(),
        )
    }

    fun find(id: String): Project? =
        jdbc.query("SELECT * FROM projects WHERE id = ?", { result, _ -> result.toProject() }, id)
            .firstOrNull()

    fun findForUpdate(id: String): Project? =
        jdbc.query(
            "SELECT * FROM projects WHERE id = ? FOR UPDATE",
            { result, _ -> result.toProject() },
            id,
        ).firstOrNull()

    fun touch(id: String, now: Instant) {
        jdbc.update("UPDATE projects SET updated_at = ? WHERE id = ?", now.toJdbcTime(), id)
    }

    fun updateCheckpoint(id: String, checkpoint: String, now: Instant) {
        jdbc.update(
            "UPDATE projects SET current_checkpoint = ?, updated_at = ? WHERE id = ?",
            checkpoint,
            now.toJdbcTime(),
            id,
        )
    }

    private fun ResultSet.toProject() = Project(
        id = getString("id"),
        name = getString("name"),
        screen = getString("screen"),
        schemaVersion = getInt("schema_version"),
        currentCheckpoint = getString("current_checkpoint"),
        referenceWidth = getInt("reference_w"),
        referenceHeight = getInt("reference_h"),
        referenceUnit = getString("reference_unit"),
        layout = getString("layout"),
        createdAt = getObject("created_at", OffsetDateTime::class.java).toInstant(),
        updatedAt = getObject("updated_at", OffsetDateTime::class.java).toInstant(),
    )
}
