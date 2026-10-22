package com.bookingsaas.common;

import com.bookingsaas.support.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Writes the API description to {@code target/openapi.json}. The frontend's
 * types are generated from it ({@code npm run gen:api}), and CI fails when
 * the committed types no longer match the API.
 */
@TestPropertySource(properties = "springdoc.api-docs.enabled=true")
class OpenApiSpecIT extends AbstractIntegrationTest {

    @Test
    void the_api_description_is_written_for_the_frontend() throws Exception {
        String spec = mockMvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(spec);
        assertThat(json.get("paths").has("/api/auth/session")).isTrue();

        // Stable output: the servers entry carries the random test port.
        ((com.fasterxml.jackson.databind.node.ObjectNode) json).remove("servers");
        Path out = Path.of("target", "openapi.json");
        Files.createDirectories(out.getParent());
        Files.writeString(out, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(json));
    }
}
