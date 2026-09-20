package com.ikk.backend.contract

import com.ikk.backend.features.projects.domain.Project
import com.ikk.backend.shared.persistence.toJdbcTime
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.core.RowCallbackHandler
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.time.Instant

@Repository
class ContractStore(
    private val jdbc: JdbcTemplate,
    private val json: JsonMapper,
) {
    fun currentTree(project: Project): ObjectNode = json.createObjectNode().apply {
        put("schemaVersion", project.schemaVersion)
        put("checkpoint", project.currentCheckpoint)
        put("screen", project.screen)
        putObject("reference").apply {
            put("w", project.referenceWidth)
            put("h", project.referenceHeight)
            put("unit", project.referenceUnit)
        }
        put("layout", project.layout)
        // Absent stays absent — emitting an empty object would make "no
        // background" indistinguishable from "a background with no fill".
        project.background?.let { set("background", read(it)) }
        val components = putObject("components")
        jdbc.query(
            "SELECT node_key, payload FROM nodes WHERE project_id = ? ORDER BY node_key",
            RowCallbackHandler { result ->
                components.set(result.getString("node_key"), read(result.getString("payload")))
            },
            project.id,
        )
    }

    @Transactional
    fun replace(projectId: String, contract: JsonNode) {
        val reference = contract.path("reference")
        jdbc.update(
            """
            UPDATE projects
            SET schema_version = ?, current_checkpoint = ?, screen = ?, reference_w = ?,
                reference_h = ?, reference_unit = ?, layout = ?, background = ?,
                updated_at = ?
            WHERE id = ?
            """.trimIndent(),
            contract.path("schemaVersion").intValue(),
            contract.path("checkpoint").stringValue(),
            contract.path("screen").stringValue(),
            reference.path("w").intValue(),
            reference.path("h").intValue(),
            reference.path("unit").stringValue(),
            contract.path("layout").stringValue(),
            contract.get("background")?.takeUnless { it.isNull }?.toString(),
            Instant.now().toJdbcTime(),
            projectId,
        )
        jdbc.update("DELETE FROM nodes WHERE project_id = ?", projectId)
        contract.path("components").properties()
            .sortedBy { it.key }
            .forEach { insert(projectId, it.key, it.value) }
    }

    fun nodesById(projectId: String): Map<String, StoredNode> = buildMap {
        jdbc.query(
            "SELECT node_key, payload FROM nodes WHERE project_id = ?",
            RowCallbackHandler { result ->
                val node = map(result.getString("node_key"), result.getString("payload"))
                put(node.id, node)
            },
            projectId,
        )
    }

    fun findNode(projectId: String, nodeId: String): StoredNode? =
        jdbc.query(
            "SELECT node_key, payload FROM nodes WHERE project_id = ? AND node_id = ?",
            { result, _ -> map(result.getString("node_key"), result.getString("payload")) },
            projectId,
            nodeId,
        ).firstOrNull()

    fun upsert(projectId: String, key: String, payload: JsonNode) {
        val canonical = CanonicalJson.canonicalJson(payload)
        val checksum = CanonicalJson.checksum(payload)
        val updated = jdbc.update(
            """
            UPDATE nodes
            SET node_key = ?, type = ?, version = ?, updated_at = ?, checksum = ?, payload = ?
            WHERE project_id = ? AND node_id = ?
            """.trimIndent(),
            key,
            payload.path("type").stringValue(),
            payload.path("version").intValue(),
            Instant.parse(payload.path("updatedAt").stringValue()).toJdbcTime(),
            checksum,
            canonical,
            projectId,
            payload.path("id").stringValue(),
        )
        if (updated == 0) insert(projectId, key, payload)
    }

    fun deleteNode(projectId: String, nodeId: String): Boolean =
        jdbc.update("DELETE FROM nodes WHERE project_id = ? AND node_id = ?", projectId, nodeId) > 0

    private fun insert(projectId: String, key: String, payload: JsonNode) {
        jdbc.update(
            """
            INSERT INTO nodes (
                project_id, node_id, node_key, type, version, updated_at, checksum, payload
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            projectId,
            payload.path("id").stringValue(),
            key,
            payload.path("type").stringValue(),
            payload.path("version").intValue(),
            Instant.parse(payload.path("updatedAt").stringValue()).toJdbcTime(),
            CanonicalJson.checksum(payload),
            CanonicalJson.canonicalJson(payload),
        )
    }

    private fun map(key: String, payload: String): StoredNode {
        val node = read(payload)
        return StoredNode(
            id = node.path("id").stringValue(),
            key = key,
            type = node.path("type").stringValue(),
            version = node.path("version").intValue(),
            updatedAt = Instant.parse(node.path("updatedAt").stringValue()),
            checksum = CanonicalJson.checksum(node),
            payload = node,
        )
    }

    private fun read(value: String): JsonNode = try {
        json.readTree(value)
    } catch (exception: RuntimeException) {
        throw IllegalStateException("stored contract JSON is invalid", exception)
    }
}

data class StoredNode(
    val id: String,
    val key: String,
    val type: String,
    val version: Int,
    val updatedAt: Instant,
    val checksum: String,
    val payload: JsonNode,
)
