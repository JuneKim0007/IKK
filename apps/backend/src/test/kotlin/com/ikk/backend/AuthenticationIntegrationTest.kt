package com.ikk.backend

import org.hamcrest.Matchers.startsWith
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

@SpringBootTest(
    properties = [
        "spring.datasource.url=jdbc:h2:mem:ikk_auth_test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "ikk.api-token=test-secret",
    ],
)
@AutoConfigureMockMvc
class AuthenticationIntegrationTest {
    @Autowired
    lateinit var mockMvc: MockMvc

    @Test
    fun `health stays public and project routes require the configured token`() {
        mockMvc.perform(get("/healthz"))
            .andExpect(status().isOk)

        mockMvc.perform(
            post("/v1/projects")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Protected"}"""),
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.error").value("unauthorized"))
            .andExpect(header().string("X-Request-ID", startsWith("req_")))

        mockMvc.perform(
            post("/v1/projects")
                .header(HttpHeaders.AUTHORIZATION, "Bearer test-secret")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"name":"Protected"}"""),
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.name").value("Protected"))
    }
}
