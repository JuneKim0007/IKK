package com.ikk.backend

import org.hamcrest.Matchers.hasSize
import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.RequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode
import java.nio.file.Files
import java.nio.file.Path

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:ikk_backend_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "ikk.api-token=",
    ],
)
@AutoConfigureMockMvc
class BackendIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var json: JsonMapper

    @Test
    fun `project contract sync checkpoint codegen and asset flow`() {
        mockMvc.perform(get("/healthz"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.status").value("ok"))
            .andExpect(header().string("X-Request-ID", startsWith("req_")))

        val project = performJson(
            post("/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Inbox app"}"""),
            201,
        )
        val projectId = project.path("id").stringValue()
        val contract = fixture("valid/all_node_types.json")

        mockMvc.perform(
            put("/v1/projects/{projectId}/contract", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(contract.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.checkpoint").value("cp_005"))
            // all_node_types.json grew a triangle and a line; the count follows
            // the fixture rather than being asserted independently of it.
            .andExpect(jsonPath("$.components").value(6))

        mockMvc.perform(get("/v1/projects/{projectId}/contract", projectId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.components.rect_header.name").value("Header"))

        mockMvc.perform(get("/v1/projects/{projectId}/checkpoints/cp_005", projectId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.components.rect_header.name").value("Header"))

        val payload = (contract.path("components").path("rect_header") as ObjectNode).deepCopy().apply {
            put("version", 4)
            put("updatedAt", "2026-09-20T15:00:00Z")
        }
        val syncBody = json.createObjectNode().apply {
            putArray("nodes").addObject().apply {
                put("id", "n1")
                put("type", "rect")
                put("schemaVersion", 1)
                put("version", 4)
                put("updatedAt", "2026-09-20T15:00:00Z")
                set("payload", payload)
            }
        }
        mockMvc.perform(
            put("/v1/projects/{projectId}/nodes", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(syncBody.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.accepted[0]").value("n1"))
            .andExpect(jsonPath("$.checksums.n1", startsWith("sha256:")))

        mockMvc.perform(
            put("/v1/projects/{projectId}/nodes", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(syncBody.toString()),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("stale_version"))

        // An id the fixture does not use, so this exercises the key collision
        // rather than tripping the stale-version check first.
        val collidingPayload = payload.deepCopy().apply {
            put("id", "n7")
            put("version", 1)
        }
        val collidingNode = json.createObjectNode().apply {
            putArray("nodes").addObject().apply {
                put("id", "n7")
                put("type", "rect")
                put("schemaVersion", 1)
                put("version", 1)
                put("updatedAt", "2026-09-20T15:00:00Z")
                set("payload", collidingPayload)
            }
        }
        mockMvc.perform(
            put("/v1/projects/{projectId}/nodes", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(collidingNode.toString()),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("node_key_conflict"))

        mockMvc.perform(post("/v1/projects/{projectId}/checkpoints", projectId))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.checkpoint").value("cp_006"))

        mockMvc.perform(
            post("/v1/projects/{projectId}/generate", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"targets":["kotlin","css","html"]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.checkpoint").value("cp_007"))
            .andExpect(jsonPath("$.artifacts", hasSize<Any>(3)))

        mockMvc.perform(get("/v1/projects/{projectId}/artifacts/home.generated.css", projectId))
            .andExpect(status().isOk)
            .andExpect(content().string(org.hamcrest.Matchers.containsString("box-sizing: border-box")))

        mockMvc.perform(
            post("/v1/projects/{projectId}/generate", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"targets":["css"]}"""),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.checkpoint").value("cp_008"))
            .andExpect(jsonPath("$.artifacts", hasSize<Any>(1)))
        mockMvc.perform(get("/v1/projects/{projectId}/artifacts/HomeLayout.generated.kt", projectId))
            .andExpect(status().isNotFound)

        mockMvc.perform(
            put("/v1/projects/{projectId}/contract", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(contract.toString()),
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.error").value("stale_checkpoint"))

        val image = MockMultipartFile(
            "file",
            "pixel.png",
            "image/png",
            byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47),
        )
        val uploaded = performJson(
            multipart("/v1/projects/{projectId}/assets", projectId).file(image),
            201,
        )
        mockMvc.perform(get("/v1/assets/{ref}", uploaded.path("ref").stringValue()))
            .andExpect(status().isOk)
            .andExpect(content().contentType("image/png"))
            .andExpect(content().bytes(image.bytes))

        mockMvc.perform(delete("/v1/projects/{projectId}/nodes/{nodeId}", projectId, "n1"))
            .andExpect(status().isNoContent)

        mockMvc.perform(
            post("/v1/contracts/validate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(fixture("invalid/V11_unknown_field.json").toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.valid").value(false))
            .andExpect(jsonPath("$.violations[0].rule").value("V11"))

        mockMvc.perform(
            put("/v1/projects/demo/contract")
                .contentType(MediaType.APPLICATION_JSON)
                .content(contract.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.projectId").value("demo"))

        mockMvc.perform(get("/v1/projects/demo"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.name").value("Home"))

        val editedDemo = (contract as ObjectNode).deepCopy().apply {
            (path("components").path("rect_header") as ObjectNode).put("fill", "#112233")
        }
        mockMvc.perform(
            put("/v1/projects/demo/contract")
                .contentType(MediaType.APPLICATION_JSON)
                .content(editedDemo.toString()),
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.checkpoint").value("cp_005"))
        mockMvc.perform(get("/v1/projects/demo/contract"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.components.rect_header.fill").value("#112233"))
        mockMvc.perform(get("/v1/projects/demo/checkpoints/cp_005"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.components.rect_header.fill").value("#65558F"))

        mockMvc.perform(
            put("/v1/projects/{projectId}/contract", "bad:id")
                .contentType(MediaType.APPLICATION_JSON)
                .content(contract.toString()),
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.error").value("invalid_project_id"))
    }

    private fun performJson(request: RequestBuilder, statusCode: Int): JsonNode {
        val result = mockMvc.perform(request)
            .andExpect(status().`is`(statusCode))
            .andReturn()
        return json.readTree(result.response.contentAsString)
    }

    private fun fixture(relative: String): JsonNode =
        json.readTree(Files.readString(repoRoot().resolve("docs/fixtures/$relative")))

    private fun repoRoot(): Path {
        var directory = Path.of("").toAbsolutePath()
        while (!Files.isDirectory(directory.resolve("docs/fixtures"))) {
            directory = directory.parent ?: error("could not locate repository root")
        }
        return directory
    }
}
