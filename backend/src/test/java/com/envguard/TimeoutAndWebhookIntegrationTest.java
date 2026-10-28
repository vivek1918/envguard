package com.envguard;

import com.envguard.domain.entity.DeploymentRequest;
import com.envguard.domain.entity.User;
import com.envguard.domain.enums.DeploymentRequestStatus;
import com.envguard.domain.enums.RoleName;
import com.envguard.repository.DeploymentRequestRepository;
import com.envguard.support.TestDataHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = EnvGuardApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TimeoutAndWebhookIntegrationTest {

    private static final String PIPELINE_TOKEN = "test-pipeline-token-do-not-use-in-prod";
    private static final String CONFIG_YAML = """
            environment: staging
            variables:
              APP_NAME: envguard
            secretReferences: {}
            """;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TestDataHelper testDataHelper;
    @Autowired DeploymentRequestRepository deploymentRequestRepository;

    @Test
    void timeoutExpiryOnStatusPoll() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User developer = testDataHelper.createUser("to_dev_" + suffix, RoleName.DEVELOPER);
        String token = login(developer.getUsername());

        MvcResult projectResult = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"to-proj-" + suffix + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String projectId = objectMapper.readTree(projectResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        String runId = "run-to-" + suffix;
        mockMvc.perform(post("/api/pipeline/builds")
                        .header("X-EnvGuard-Pipeline-Token", PIPELINE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","commitSha":"c1","branch":"main","pipelineRunId":"%s","artifactDigest":"sha256:t","status":"SUCCEEDED"}
                                """.formatted(projectId, runId)))
                .andExpect(status().isCreated());

        MvcResult dep = mockMvc.perform(post("/api/pipeline/deployments")
                        .header("X-EnvGuard-Pipeline-Token", PIPELINE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId":"%s",
                                  "environment":"staging",
                                  "configYaml":%s,
                                  "commitSha":"c1",
                                  "artifactDigest":"sha256:t",
                                  "pipelineRunId":"%s",
                                  "requesterIdentity":"%s",
                                  "idempotencyKey":"to-key-%s"
                                }
                                """.formatted(projectId, objectMapper.writeValueAsString(CONFIG_YAML),
                                runId, developer.getUsername(), suffix)))
                .andExpect(status().isCreated())
                .andReturn();
        String deploymentId = objectMapper.readTree(dep.getResponse().getContentAsString())
                .path("data").path("id").asText();

        DeploymentRequest dr = deploymentRequestRepository.findById(UUID.fromString(deploymentId)).orElseThrow();
        dr.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        deploymentRequestRepository.save(dr);

        mockMvc.perform(get("/api/pipeline/deployments/" + deploymentId + "/status")
                        .header("X-EnvGuard-Pipeline-Token", PIPELINE_TOKEN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("TIMED_OUT"));

        DeploymentRequest refreshed = deploymentRequestRepository.findById(UUID.fromString(deploymentId)).orElseThrow();
        assertThat(refreshed.getStatus()).isEqualTo(DeploymentRequestStatus.TIMED_OUT);
    }

    @Test
    void duplicateWebhookHandling() throws Exception {
        String payload = """
                {
                  "ref":"refs/heads/main",
                  "after":"abc123",
                  "delivery_id":"deliv-%s",
                  "repository":{"id":1,"name":"repo","owner":{"login":"acme"}},
                  "head_commit":{"id":"abc123","author":{"username":"bot"}}
                }
                """.formatted(UUID.randomUUID().toString().substring(0, 8));

        mockMvc.perform(post("/api/webhooks/GITHUB")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACCEPTED"));

        mockMvc.perform(post("/api/webhooks/GITHUB")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DUPLICATE"));
    }

    @Test
    void auditEventCreatedOnRegister() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"aud_%s","email":"aud_%s@ex.com","password":"password123"}
                                """.formatted(suffix, suffix)))
                .andExpect(status().isCreated());

        String token = login("aud_" + suffix);
        mockMvc.perform(get("/api/audit?action=USER_REGISTERED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].action").value("USER_REGISTERED"));
    }

    private String login(String username) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"%s\",\"password\":\"password123\"}".formatted(username)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }
}
