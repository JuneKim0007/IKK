package com.ikk.backend.features.checkpoints.application

import com.ikk.backend.contract.CanonicalJson
import com.ikk.backend.contract.ContractService
import com.ikk.backend.contract.ContractStore
import com.ikk.backend.features.checkpoints.domain.Checkpoint
import com.ikk.backend.features.checkpoints.infrastructure.CheckpointRepository
import com.ikk.backend.features.projects.application.ProjectService
import com.ikk.backend.features.projects.infrastructure.ProjectRepository
import com.ikk.backend.shared.api.ApiException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import java.math.BigInteger
import java.time.Instant

@Service
class CheckpointService(
    private val projects: ProjectService,
    private val projectRepository: ProjectRepository,
    private val contracts: ContractStore,
    private val contractService: ContractService,
    private val checkpoints: CheckpointRepository,
    private val json: JsonMapper,
) {
    @Transactional
    fun create(projectId: String): Checkpoint {
        val project = projects.requireForUpdate(projectId)
        val snapshot = contracts.currentTree(project)
        val maxStoredOrdinal = checkpoints.list(projectId)
            .maxOfOrNull { it.checkpoint.checkpointOrdinal() }
            ?: BigInteger.ZERO
        val nextOrdinal = maxOf(maxStoredOrdinal, project.currentCheckpoint.checkpointOrdinal()) + BigInteger.ONE
        val checkpoint = "cp_${nextOrdinal.toString().padStart(3, '0')}"
        snapshot.put("checkpoint", checkpoint)
        contractService.requireValid(snapshot)
        val now = Instant.now()
        checkpoints.insert(projectId, checkpoint, CanonicalJson.canonicalJson(snapshot), now)
        projectRepository.updateCheckpoint(projectId, checkpoint, now)
        return Checkpoint(checkpoint, now)
    }

    @Transactional
    fun importSnapshot(projectId: String, contract: JsonNode) {
        val checkpoint = contract.path("checkpoint").stringValue()
        val snapshot = CanonicalJson.canonicalJson(contract)
        val existing = checkpoints.snapshot(projectId, checkpoint)
        if (existing != null) return
        checkpoints.insert(projectId, checkpoint, snapshot, Instant.now())
    }

    fun list(projectId: String): List<Checkpoint> {
        projects.require(projectId)
        return checkpoints.list(projectId)
    }

    fun get(projectId: String, checkpoint: String): JsonNode {
        projects.require(projectId)
        val snapshot = checkpoints.snapshot(projectId, checkpoint) ?: throw ApiException(
            HttpStatus.NOT_FOUND,
            "checkpoint_not_found",
            "checkpoint $checkpoint was not found",
        )
        return try {
            json.readTree(snapshot)
        } catch (exception: RuntimeException) {
            throw IllegalStateException("stored checkpoint is invalid", exception)
        }
    }

    private fun String.checkpointOrdinal(): BigInteger =
        removePrefix("cp_").toBigInteger()
}
