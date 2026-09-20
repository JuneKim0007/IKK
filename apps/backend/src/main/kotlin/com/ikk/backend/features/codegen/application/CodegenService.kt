package com.ikk.backend.features.codegen.application

import com.ikk.backend.contract.CanonicalJson
import com.ikk.backend.features.checkpoints.application.CheckpointService
import com.ikk.backend.features.codegen.domain.Artifact
import com.ikk.backend.features.codegen.infrastructure.ArtifactRepository
import com.ikk.backend.features.projects.application.ProjectService
import com.ikk.backend.shared.api.ApiException
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import tools.jackson.databind.JsonNode
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.util.Comparator
import java.util.Locale
import java.util.concurrent.TimeUnit

@Service
class CodegenService(
    private val projects: ProjectService,
    private val checkpoints: CheckpointService,
    private val artifacts: ArtifactRepository,
    @Value("\${ikk.codegen-script:packages/codegen/generate.mjs}") script: String,
    @Value("\${ikk.node-executable:node}") private val nodeExecutable: String,
) {
    private val script = resolveScript(script)

    @Transactional
    fun generate(projectId: String, requestedTargets: List<String>?): GenerateResult {
        projects.require(projectId)
        val targets = normalizeTargets(requestedTargets)
        val checkpoint = checkpoints.create(projectId)
        val contract = checkpoints.get(projectId, checkpoint.checkpoint)
        var temporary: Path? = null
        try {
            requireScript()
            temporary = Files.createTempDirectory("ikk-codegen-")
            val input = temporary.resolve("contract.json")
            val css = temporary.resolve("screen.generated.css")
            val html = temporary.resolve("screen.generated.html")
            val kotlin = temporary.resolve("Screen.generated.kt")
            Files.writeString(input, CanonicalJson.canonicalJson(contract), StandardCharsets.UTF_8)

            val process = ProcessBuilder(
                nodeExecutable,
                script.toString(),
                "--input", input.toString(),
                "--css", css.toString(),
                "--html", html.toString(),
                "--kotlin", kotlin.toString(),
            ).redirectErrorStream(true).start()
            if (!process.waitFor(30, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                throw ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "codegen_timeout",
                    "code generation timed out",
                )
            }
            val output = process.inputStream.readAllBytes().toString(StandardCharsets.UTF_8)
            if (process.exitValue() != 0) {
                throw ApiException(
                    HttpStatus.UNPROCESSABLE_CONTENT,
                    "codegen_failed",
                    output.trim().ifBlank { "code generation failed" },
                )
            }

            val screen = contract.path("screen").stringValue().safeScreen()
            val generated = buildList {
                if ("css" in targets) {
                    add(readArtifact(projectId, "${screen.lowerFirst()}.generated.css", "css", "text/css", css))
                }
                if ("html" in targets) {
                    add(readArtifact(projectId, "${screen.lowerFirst()}.generated.html", "html", "text/html", html))
                }
                if ("kotlin" in targets) {
                    add(readArtifact(projectId, "${screen}Layout.generated.kt", "kotlin", "text/plain", kotlin))
                }
            }
            artifacts.deleteAll(projectId)
            generated.forEach(artifacts::upsert)
            return GenerateResult(checkpoint.checkpoint, generated)
        } catch (exception: ApiException) {
            throw exception
        } catch (exception: IOException) {
            throw ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "codegen_unavailable",
                "could not start or read the code generator: ${exception.message}",
            )
        } catch (exception: InterruptedException) {
            Thread.currentThread().interrupt()
            throw ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "codegen_interrupted",
                "code generation was interrupted",
            )
        } finally {
            temporary?.deleteRecursively()
        }
    }

    fun requireArtifact(projectId: String, name: String): Artifact {
        projects.require(projectId)
        if (name.contains('/') || name.contains('\\') || name.contains("..")) {
            throw ApiException(HttpStatus.BAD_REQUEST, "invalid_artifact_name", "invalid artifact name")
        }
        return artifacts.find(projectId, name) ?: throw ApiException(
            HttpStatus.NOT_FOUND,
            "artifact_not_found",
            "artifact $name was not found",
        )
    }

    private fun readArtifact(
        projectId: String,
        name: String,
        target: String,
        mime: String,
        path: Path,
    ): Artifact {
        val content = Files.readString(path, StandardCharsets.UTF_8)
        return Artifact(
            projectId = projectId,
            name = name,
            target = target,
            mime = "$mime; charset=UTF-8",
            bytes = content.toByteArray(StandardCharsets.UTF_8).size.toLong(),
            content = content,
            createdAt = Instant.now(),
        )
    }

    private fun requireScript() {
        if (!Files.isRegularFile(script)) {
            throw ApiException(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "codegen_unavailable",
                "code generator was not found at $script",
            )
        }
    }

    private fun normalizeTargets(requested: List<String>?): Set<String> {
        if (requested.isNullOrEmpty()) return linkedSetOf("kotlin", "css", "html")
        return requested.mapTo(linkedSetOf()) { raw ->
            raw.lowercase(Locale.ROOT).also { target ->
                if (target !in TARGETS) {
                    throw ApiException(
                        HttpStatus.BAD_REQUEST,
                        "unsupported_target",
                        "supported targets are kotlin, css, and html",
                    )
                }
            }
        }
    }

    private fun String.safeScreen(): String {
        var cleaned = replace(Regex("[^A-Za-z0-9]"), "").ifBlank { "Screen" }
        if (cleaned.first().isDigit()) cleaned = "Screen$cleaned"
        return cleaned.replaceFirstChar(Char::uppercaseChar)
    }

    private fun String.lowerFirst(): String = replaceFirstChar(Char::lowercaseChar)

    private fun Path.deleteRecursively() {
        runCatching {
            Files.walk(this).use { paths ->
                paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)
            }
        }
    }

    companion object {
        private val TARGETS = setOf("css", "html", "kotlin")

        private fun resolveScript(configured: String): Path {
            val path = Path.of(configured)
            if (path.isAbsolute) return path.normalize()

            return generateSequence(Path.of("").toAbsolutePath()) { it.parent }
                .map { it.resolve(path).normalize() }
                .firstOrNull(Files::isRegularFile)
                ?: path.toAbsolutePath().normalize()
        }
    }
}

data class GenerateResult(val checkpoint: String, val artifacts: List<Artifact>)
