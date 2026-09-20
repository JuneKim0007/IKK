package com.ikk.backend.features.projects.api

import com.ikk.backend.contract.ContractService
import com.ikk.backend.contract.ContractStore
import com.ikk.backend.features.checkpoints.application.CheckpointService
import com.ikk.backend.features.projects.application.ProjectService
import com.ikk.backend.shared.api.ApiException
import com.ikk.core.contract.Violation
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import java.math.BigInteger

@RestController
@RequestMapping("/v1")
class ProjectContractController(
    private val projects: ProjectService,
    private val contracts: ContractStore,
    private val contractService: ContractService,
    private val checkpoints: CheckpointService,
) {
    @GetMapping("/projects/{projectId}/contract")
    @Transactional
    fun current(@PathVariable projectId: String): JsonNode =
        contracts.currentTree(projects.requireForUpdate(projectId))

    @PutMapping("/projects/{projectId}/contract")
    @Transactional
    fun replace(
        @PathVariable projectId: String,
        @RequestBody contract: JsonNode,
    ): ContractStoredResponse {
        val normalized = contractService.normalize(contract)
        val project = projects.ensureForImport(projectId, normalized)
        if (normalized.path("checkpoint").checkpointOrdinal() < project.currentCheckpoint.checkpointOrdinal()) {
            throw ApiException(
                HttpStatus.CONFLICT,
                "stale_checkpoint",
                "incoming checkpoint is older than ${project.currentCheckpoint}",
            )
        }
        contracts.replace(projectId, normalized)
        checkpoints.importSnapshot(projectId, normalized)
        return ContractStoredResponse(
            projectId,
            normalized.path("checkpoint").stringValue(),
            normalized.path("components").size(),
        )
    }

    @PostMapping("/projects/{projectId}/validate")
    fun validateProject(
        @PathVariable projectId: String,
        @RequestBody(required = false) candidate: JsonNode?,
    ): ValidationResponse {
        val project = projects.require(projectId)
        return validationResponse(candidate ?: contracts.currentTree(project))
    }

    @PostMapping("/contracts/validate")
    fun validate(@RequestBody candidate: JsonNode): ValidationResponse = validationResponse(candidate)

    private fun validationResponse(tree: JsonNode): ValidationResponse {
        val violations = contractService.validate(tree)
        return ValidationResponse(
            valid = violations.isEmpty(),
            violations = violations,
            screen = tree.path("screen").takeIf(JsonNode::isString)?.stringValue(),
            components = tree.path("components").takeIf(JsonNode::isObject)?.size() ?: 0,
        )
    }

    private fun JsonNode.checkpointOrdinal(): BigInteger = stringValue().checkpointOrdinal()

    private fun String.checkpointOrdinal(): BigInteger = removePrefix("cp_").toBigInteger()
}

data class ContractStoredResponse(val projectId: String, val checkpoint: String, val components: Int)

data class ValidationResponse(
    val valid: Boolean,
    val violations: List<Violation>,
    val screen: String?,
    val components: Int,
)
