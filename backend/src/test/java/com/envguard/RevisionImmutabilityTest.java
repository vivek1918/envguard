package com.envguard;

import com.envguard.domain.entity.ConfigurationRevision;
import com.envguard.domain.entity.User;
import com.envguard.domain.enums.RoleName;
import com.envguard.repository.ConfigurationRevisionRepository;
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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = EnvGuardApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RevisionImmutabilityTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired TestDataHelper testDataHelper;
    @Autowired ConfigurationRevisionRepository revisionRepository;

    @Test
    void revisionFieldsRemainImmutableAfterCreate() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        User developer = testDataHelper.createUser("imm_" + suffix, RoleName.DEVELOPER);
        String token = login(developer.getUsername());

        MvcResult projectResult = mockMvc.perform(post("/api/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"imm-proj-" + suffix + "\"}"))
                .andExpect(status().isCreated())
                .andReturn();
        String projectId = objectMapper.readTree(projectResult.getResponse().getContentAsString())
                .path("data").path("id").asText();

        String yaml = """
                environment: staging
                variables:
                  APP_NAME: v1
                secretReferences: {}
                """;
        MvcResult validate = mockMvc.perform(post("/api/projects/" + projectId + "/configurations/validate")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"environment":"staging","configYaml":%s,"commitSha":"c1"}
                                """.formatted(objectMapper.writeValueAsString(yaml))))
                .andExpect(status().isOk())
                .andReturn();

        // endpoint returns 200 OK
        String revisionId = objectMapper.readTree(validate.getResponse().getContentAsString())
                .path("data").path("revisionId").asText();
        String checksum = objectMapper.readTree(validate.getResponse().getContentAsString())
                .path("data").path("checksum").asText();

        ConfigurationRevision revision = revisionRepository.findById(UUID.fromString(revisionId)).orElseThrow();
        String originalYaml = revision.getConfigYaml();
        String originalChecksum = revision.getChecksum();

        // Attempt in-memory mutation then save — updatable=false columns must not change in DB
        revision.setApprovalStatus(com.envguard.domain.enums.RevisionApprovalStatus.APPROVED);
        revisionRepository.saveAndFlush(revision);

        ConfigurationRevision reloaded = revisionRepository.findById(UUID.fromString(revisionId)).orElseThrow();
        assertThat(reloaded.getConfigYaml()).isEqualTo(originalYaml);
        assertThat(reloaded.getChecksum()).isEqualTo(originalChecksum).isEqualTo(checksum);
        assertThat(reloaded.getSourceCommitSha()).isEqualTo("c1");
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
