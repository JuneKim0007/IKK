package com.ikk.backend.features.assets.infrastructure

import com.ikk.backend.features.assets.domain.Asset
import com.ikk.backend.shared.persistence.toJdbcTime
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.time.OffsetDateTime

@Repository
class AssetRepository(private val jdbc: JdbcTemplate) {
    fun insert(asset: Asset) {
        jdbc.update(
            """
            INSERT INTO assets (
                ref, project_id, original_name, mime, byte_count, content, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            asset.ref,
            asset.projectId,
            asset.originalName,
            asset.mime,
            asset.bytes,
            asset.content,
            asset.createdAt.toJdbcTime(),
        )
    }

    fun find(ref: String): Asset? =
        jdbc.query("SELECT * FROM assets WHERE ref = ?", { result, _ ->
            Asset(
                ref = result.getString("ref"),
                projectId = result.getString("project_id"),
                originalName = result.getString("original_name"),
                mime = result.getString("mime"),
                bytes = result.getLong("byte_count"),
                content = result.getBytes("content"),
                createdAt = result.getObject("created_at", OffsetDateTime::class.java).toInstant(),
            )
        }, ref).firstOrNull()
}
