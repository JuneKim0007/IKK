package com.ikk.backend.features.codegen.infrastructure

import com.ikk.backend.features.codegen.domain.Artifact
import com.ikk.backend.shared.persistence.toJdbcTime
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class ArtifactRepository(private val jdbc: JdbcTemplate) {
    fun deleteAll(projectId: String) {
        jdbc.update("DELETE FROM artifacts WHERE project_id = ?", projectId)
    }

    fun upsert(artifact: Artifact) {
        val updated = jdbc.update(
            """
            UPDATE artifacts
            SET target = ?, mime = ?, byte_count = ?, content = ?, created_at = ?
            WHERE project_id = ? AND name = ?
            """.trimIndent(),
            artifact.target,
            artifact.mime,
            artifact.bytes,
            artifact.content,
            artifact.createdAt.toJdbcTime(),
            artifact.projectId,
            artifact.name,
        )
        if (updated == 0) {
            jdbc.update(
                """
                INSERT INTO artifacts (
                    project_id, name, target, mime, byte_count, content, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
                artifact.projectId,
                artifact.name,
                artifact.target,
                artifact.mime,
                artifact.bytes,
                artifact.content,
                artifact.createdAt.toJdbcTime(),
            )
        }
    }

    fun find(projectId: String, name: String): Artifact? =
        jdbc.query(
            "SELECT * FROM artifacts WHERE project_id = ? AND name = ?",
            { result, _ ->
                Artifact(
                    projectId = result.getString("project_id"),
                    name = result.getString("name"),
                    target = result.getString("target"),
                    mime = result.getString("mime"),
                    bytes = result.getLong("byte_count"),
                    content = result.getString("content"),
                    createdAt = result.getObject("created_at", OffsetDateTime::class.java).toInstant(),
                )
            },
            projectId,
            name,
        ).firstOrNull()
}
