package com.envguard;

import com.envguard.domain.entity.User;
import com.envguard.domain.enums.RoleName;
import com.envguard.support.TestDataHelper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = EnvGuardApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeploymentWorkflowIntegrationTest {

    private static final String PIPELINE_TOKEN = "test-pipeline-token-do-not-use-in-prod";
    private static final String CONFIG_YAML = """
            environment: staging
            variables:
              APP_NAME: envguard
              LOG_LEVEL: INFO
            secretReferences:
              DB_PASSWORD: arn:aws:secretsmanager:us-east-1:123:secret:db
            """;

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TestDataHelper testDataHelper;

    User developer;
    User viewer;
    User approver2;
    String developerToken;
    String viewerToken;
    String approver2Token;
    String projectId;
    String envStagingId;

    @BeforeEach
    void setUp() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        developer = testDataHelper.createUser("dev_" + suffix, RoleName.DEVELOPER);
        viewer = testDataHelper.createUser("view_" + suffix, RoleName.VIEWER);
        approver2 = testDataHelper.createUser("apr2_" + suffix, RoleName.DEVELOPER);

        developerToken = login(developer.getUsername());
        viewerToken = login(viewer.getUsername());
        approver2Token = login(approver2.getUsername());

        MvcResult projectResult = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + developerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"proj-" + suffix + "\",\"description\":\"test\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        projectId = objectMapper.readTree(projectResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + developerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","role":"VIEWER"}
                                """.formatted(viewer.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/projects/" + projectId + "/members")
                        .header("Authorization", "Bearer " + developerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"%s","role":"DEVELOPER"}
                                """.formatted(approver2.getId())))
                .andExpect(status().isOk());

        MvcResult envs = mockMvc.perform(get("/api/projects/" + projectId + "/environments")
                        .header("Authorization", "Bearer " + developerToken))
                .andExpect(status().isOk())
                .andReturn();
        for (JsonNode env : objectMapper.readTree(envs.getResponse().getContentAsString()).path("data")) {
            if ("staging".equals(env.path("name").asText())) {
                envStagingId = env.path("id").asText();
            }
        }
        assertThat(envStagingId).isNotBlank();
    }

    @Test
    void configValidationDiffAndImmutability() throws Exception {
        MvcResult validate = mockMvc.perform(post("/api/projects/" + projectId + "/configurations/validate")
                        .header("Authorization", "Bearer " + developerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"environment":"staging","configYaml":%s,"commitSha":"abc123","artifactDigest":"sha256:art1"}
                                """.formatted(objectMapper.writeValueAsString(CONFIG_YAML))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.valid").value(true))
                .andExpect(jsonPath("$.data.revisionId").isNotEmpty())
                .andExpect(jsonPath("$.data.diff.addedVariables").isArray())
                .andReturn();

        JsonNode report = objectMapper.readTree(validate.getResponse().getContentAsString()).path("data");
        String revisionId = report.path("revisionId").asText();
        String checksum = report.path("checksum").asText();
        assertThat(checksum).isNotBlank();
        assertThat(report.path("secretAvailability").path("DB_PASSWORD").asText()).isIn("SKIPPED", "AVAILABLE");

        mockMvc.perform(get("/api/projects/" + projectId + "/configurations/" + revisionId)
                        .header("Authorization", "Bearer " + developerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checksum").value(checksum))
                .andExpect(jsonPath("$.data.variables[?(@.secretRef==true)].value").value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.nullValue())));

        mockMvc.perform(get("/api/projects/" + projectId + "/configurations?environment=staging")
                        .header("Authorization", "Bearer " + developerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(revisionId));
    }

    @Test
    void viewerCannotApprove_developerCanApproveAndReject() throws Exception {
        String deploymentId = createPipelineDeployment("idem-approve-" + UUID.randomUUID(), "sha256:art-ok");
        JsonNode deployment = getDeployment(deploymentId, developerToken);
        String checksum = deployment.path("checksum").asText();
        String digest = deployment.path("artifactDigest").asText();

        mockMvc.perform(post("/api/deployments/" + deploymentId + "/approve")
                        .header("Authorization", "Bearer " + viewerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"checksum":"%s","artifactDigest":"%s"}
                                """.formatted(checksum, digest)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/deployments/" + deploymentId + "/approve")
                        .header("Authorization", "Bearer " + developerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"checksum":"%s","artifactDigest":"%s","comment":"lgtm"}
                                """.formatted(checksum, digest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"));

        // New deployment for reject path
        String rejectId = createPipelineDeployment("idem-reject-" + UUID.randomUUID(), "sha256:art-rej");
        JsonNode rejectDep = getDeployment(rejectId, developerToken);
        mockMvc.perform(post("/api/deployments/" + rejectId + "/reject")
                        .header("Authorization", "Bearer " + developerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"checksum":"%s","artifactDigest":"%s","comment":"nope"}
                                """.formatted(rejectDep.path("checksum").asText(),
                                rejectDep.path("artifactDigest").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"));
    }

    @Test
    void unauthorizedApprovalWrongChecksumFails() throws Exception {
        String deploymentId = createPipelineDeployment("idem-bad-" + UUID.randomUUID(), "sha256:art-x");
        mockMvc.perform(post("/api/deployments/" + deploymentId + "/approve")
                        .header("Authorization", "Bearer " + developerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"checksum":"deadbeef","artifactDigest":"sha256:art-x"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void artifactAndConfigMismatchOnVerify() throws Exception {
        String deploymentId = createPipelineDeployment("idem-verify-" + UUID.randomUUID(), "sha256:good");
        JsonNode deployment = getDeployment(deploymentId, developerToken);
        String checksum = deployment.path("checksum").asText();
        String digest = deployment.path("artifactDigest").asText();
        String commit = deployment.path("commitSha").asText();

        mockMvc.perform(post("/api/deployments/" + deploymentId + "/approve")
                        .header("Authorization", "Bearer " + developerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"checksum":"%s","artifactDigest":"%s"}
                                """.formatted(checksum, digest)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/pipeline/deployments/" + deploymentId + "/verify")
                        .header("X-EnvGuard-Pipeline-Token", PIPELINE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"checksum":"%s","artifactDigest":"sha256:WRONG","commitSha":"%s","environment":"staging"}
                                """.formatted(checksum, commit)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verified").value(false))
                .andExpect(jsonPath("$.data.mismatches[0]").value("artifactDigest"));

        String deploymentId2 = createPipelineDeployment("idem-verify2-" + UUID.randomUUID(), "sha256:good2");
        JsonNode d2 = getDeployment(deploymentId2, developerToken);
        mockMvc.perform(post("/api/deployments/" + deploymentId2 + "/approve")
                        .header("Authorization", "Bearer " + developerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"checksum":"%s","artifactDigest":"%s"}
                                """.formatted(d2.path("checksum").asText(), d2.path("artifactDigest").asText())))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/pipeline/deployments/" + deploymentId2 + "/verify")
                        .header("X-EnvGuard-Pipeline-Token", PIPELINE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"checksum":"wrongchecksum","artifactDigest":"%s","commitSha":"%s","environment":"staging"}
                                """.formatted(d2.path("artifactDigest").asText(), d2.path("commitSha").asText())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.verified").value(false))
                .andExpect(jsonPath("$.data.mismatches").isArray());
    }

    @Test
    void idempotentDeploymentCreateAndAuditEvents() throws Exception {
        String key = "idem-dup-" + UUID.randomUUID();
        String id1 = createPipelineDeployment(key, "sha256:idem");
        String id2 = createPipelineDeployment(key, "sha256:idem");
        assertThat(id1).isEqualTo(id2);

        mockMvc.perform(get("/api/deployments/" + id1 + "/audit")
                        .header("Authorization", "Bearer " + developerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());

        mockMvc.perform(get("/api/audit")
                        .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray());

        mockMvc.perform(get("/api/dashboard/stats")
                        .header("Authorization", "Bearer " + developerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projects").isNumber());
    }

    private String createPipelineDeployment(String idempotencyKey, String artifactDigest) throws Exception {
        String runId = "run-" + UUID.randomUUID();
        mockMvc.perform(post("/api/pipeline/builds")
                        .header("X-EnvGuard-Pipeline-Token", PIPELINE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","commitSha":"commit1","branch":"main","pipelineRunId":"%s","artifactDigest":"%s","status":"SUCCEEDED"}
                                """.formatted(projectId, runId, artifactDigest)))
                .andExpect(status().isCreated());

        MvcResult result = mockMvc.perform(post("/api/pipeline/deployments")
                        .header("X-EnvGuard-Pipeline-Token", PIPELINE_TOKEN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "projectId":"%s",
                                  "environment":"staging",
                                  "configYaml":%s,
                                  "commitSha":"commit1",
                                  "artifactDigest":"%s",
                                  "pipelineRunId":"%s",
                                  "requesterIdentity":"%s",
                                  "idempotencyKey":"%s"
                                }
                                """.formatted(projectId, objectMapper.writeValueAsString(CONFIG_YAML),
                                artifactDigest, runId, developer.getUsername(), idempotencyKey)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("READY_FOR_APPROVAL"))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("id").asText();
    }

    private JsonNode getDeployment(String id, String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/deployments/" + id)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
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
