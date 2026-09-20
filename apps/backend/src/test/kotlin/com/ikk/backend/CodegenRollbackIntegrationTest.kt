package com.ikk.backend

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import tools.jackson.databind.json.JsonMapper
import java.nio.file.Files
import java.nio.file.Path

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:ikk_codegen_rollback;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "ikk.api-token=",
        "ikk.codegen-script=/definitely/missing/ikk-codegen.mjs",
    ],
)
@AutoConfigureMockMvc
class CodegenRollbackIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Autowired
    lateinit var json: JsonMapper

    @Test
    fun `failed codegen rolls back its checkpoint`() {
        val project = mockMvc.perform(
            post("/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Rollback"}"""),
        )
            .andExpect(status().isCreated)
            .andReturn()
        val projectId = json.readTree(project.response.contentAsString).path("id").stringValue()
        val contract = Files.readString(repoRoot().resolve("docs/fixtures/valid/minimal.json"))

        mockMvc.perform(
            put("/v1/projects/{projectId}/contract", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(contract),
        ).andExpect(status().isOk)

        mockMvc.perform(
            post("/v1/projects/{projectId}/generate", projectId)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"targets":["css"]}"""),
        )
            .andExpect(status().isInternalServerError)
            .andExpect(jsonPath("$.error").value("codegen_unavailable"))

        mockMvc.perform(get("/v1/projects/{projectId}", projectId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.latestCheckpoint").value("cp_001"))

        mockMvc.perform(get("/v1/projects/{projectId}/checkpoints", projectId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.checkpoints.length()").value(1))
            .andExpect(jsonPath("$.checkpoints[0].checkpoint").value("cp_001"))
    }

    private fun repoRoot(): Path = generateSequence(Path.of("").toAbsolutePath()) { it.parent }
        .first { Files.isDirectory(it.resolve("docs/fixtures")) }
}
