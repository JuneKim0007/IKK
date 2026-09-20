package com.ikk.backend.features.checkpoints.infrastructure

import com.ikk.backend.features.checkpoints.domain.Checkpoint
import com.ikk.backend.shared.persistence.toJdbcTime
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.Instant
import java.time.OffsetDateTime

@Repository
class CheckpointRepository(private val jdbc: JdbcTemplate) {
    fun insert(projectId: String, checkpoint: String, snapshot: String, createdAt: Instant) {
        jdbc.update(
            """
            INSERT INTO checkpoints (project_id, checkpoint, snapshot, created_at)
            VALUES (?, ?, ?, ?)
            """.trimIndent(),
            projectId,
            checkpoint,
            snapshot,
            createdAt.toJdbcTime(),
        )
    }

    fun list(projectId: String): List<Checkpoint> =
        jdbc.query(
            """
            SELECT checkpoint, created_at FROM checkpoints
            WHERE project_id = ? ORDER BY created_at DESC, checkpoint DESC
            """.trimIndent(),
            { result, _ ->
                Checkpoint(
                    result.getString("checkpoint"),
                    result.getObject("created_at", OffsetDateTime::class.java).toInstant(),
                )
            },
            projectId,
        )

    fun snapshot(projectId: String, checkpoint: String): String? =
        jdbc.query(
            "SELECT snapshot FROM checkpoints WHERE project_id = ? AND checkpoint = ?",
            { result, _ -> result.getString("snapshot") },
            projectId,
            checkpoint,
        ).firstOrNull()
}
