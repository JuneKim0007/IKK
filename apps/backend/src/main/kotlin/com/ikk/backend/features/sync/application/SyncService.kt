package com.ikk.backend.features.sync.application

import com.ikk.backend.contract.BackendContract
import com.ikk.backend.contract.CanonicalJson
import com.ikk.backend.contract.ContractService
import com.ikk.backend.contract.ContractStore
import com.ikk.backend.features.projects.application.ProjectService
import com.ikk.backend.features.sync.api.BatchUpsertRequest
import com.ikk.backend.features.sync.api.BatchUpsertResponse
import com.ikk.backend.features.sync.api.NodeEnvelope
import com.ikk.backend.features.sync.api.RejectedNode
import com.ikk.backend.shared.api.ApiException
import com.ikk.core.contract.Contract
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.node.ObjectNode
import java.time.Instant

@Service
class SyncService(
    private val projects: ProjectService,
    private val contracts: ContractStore,
    private val contractService: ContractService,
) {
    @Transactional
    fun upsert(projectId: String, request: BatchUpsertRequest): BatchUpsertResponse {
        validateEnvelopes(request.nodes)
        val project = projects.requireForUpdate(projectId)
        val stored = contracts.nodesById(projectId)

        val rejected = request.nodes.mapNotNull { envelope ->
            stored[envelope.id]
                ?.takeIf { envelope.version <= it.version }
                ?.let { RejectedNode(envelope.id, "stale_version", it.payload) }
        }
        if (rejected.isNotEmpty()) {
            throw ApiException(
                HttpStatus.CONFLICT,
                "stale_version",
                "one or more nodes are not newer than the stored version",
                rejected,
            )
        }

        val requestedIds = request.nodes.mapTo(mutableSetOf(), NodeEnvelope::id)
        val keys = request.nodes.associate { it.id to derivedKey(it) }
        val duplicateKey = keys.entries.groupBy { it.value }.values.firstOrNull { it.size > 1 }
        if (duplicateKey != null) {
            throw ApiException(
                HttpStatus.CONFLICT,
                "node_key_conflict",
                "multiple nodes derive the key ${duplicateKey.first().value}",
                duplicateKey.map { it.key },
            )
        }
        val storedByKey = stored.values.associateBy { it.key }
        keys.forEach { (id, key) ->
            val owner = storedByKey[key]
            if (owner != null && owner.id != id && owner.id !in requestedIds) {
                throw ApiException(
                    HttpStatus.CONFLICT,
                    "node_key_conflict",
                    "component key $key is already used by node ${owner.id}",
                    listOf(owner.payload),
                )
            }
        }

        val candidate = contracts.currentTree(project).deepCopy()
        val components = candidate.path("components") as ObjectNode
        request.nodes.mapNotNull { stored[it.id]?.key }.forEach(components::remove)
        request.nodes.forEach { envelope ->
            components.set(keys.getValue(envelope.id), envelope.payload.deepCopy())
        }
        val normalized = contractService.normalize(candidate)

        val checksums = linkedMapOf<String, String>()
        request.nodes.forEach { envelope ->
            checksums[envelope.id] = CanonicalJson.checksum(envelope.payload)
        }
        contracts.replace(projectId, normalized)
        return BatchUpsertResponse(request.nodes.map(NodeEnvelope::id), emptyList(), checksums)
    }

    @Transactional
    fun delete(projectId: String, nodeId: String) {
        projects.requireForUpdate(projectId)
        if (!contracts.deleteNode(projectId, nodeId)) {
            throw ApiException(HttpStatus.NOT_FOUND, "node_not_found", "node $nodeId was not found")
        }
        projects.touch(projectId)
    }

    private fun validateEnvelopes(envelopes: List<NodeEnvelope>) {
        val ids = mutableSetOf<String>()
        envelopes.forEach { envelope ->
            if (!ids.add(envelope.id)) invalid("duplicate node ${envelope.id} in the batch")
            val payload = envelope.payload
            if (envelope.schemaVersion != Contract.SCHEMA_VERSION) {
                invalid("unsupported schemaVersion for node ${envelope.id}")
            }
            if (envelope.id != payload.path("id").stringValue() ||
                envelope.type != payload.path("type").stringValue() ||
                envelope.version != payload.path("version").intValue()
            ) {
                invalid("envelope fields disagree with payload for node ${envelope.id}")
            }
            val payloadTime = runCatching {
                Instant.parse(payload.path("updatedAt").stringValue())
            }.getOrElse { invalid("payload updatedAt is invalid for node ${envelope.id}") }
            if (envelope.updatedAt != payloadTime) {
                invalid("envelope updatedAt disagrees with payload for node ${envelope.id}")
            }
        }
    }

    private fun derivedKey(envelope: NodeEnvelope): String = try {
        BackendContract.derivedKey(envelope.payload)
    } catch (exception: IllegalArgumentException) {
        invalid("invalid component name or type for node ${envelope.id}: ${exception.message}")
    }

    private fun invalid(message: String): Nothing = throw ApiException(
        HttpStatus.UNPROCESSABLE_CONTENT,
        "invalid_node_envelope",
        message,
    )
}
