package com.envguard.security;

import com.envguard.dto.request.RegisterRequest;
import com.envguard.service.AuthService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = com.envguard.EnvGuardApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired AuthService authService;

    @Test
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void projectsRequireJwt() throws Exception {
        mockMvc.perform(get("/api/projects"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pipelineRequiresToken() throws Exception {
        mockMvc.perform(post("/api/pipeline/builds")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pipelineAcceptsValidToken() throws Exception {
        mockMvc.perform(get("/api/pipeline/deployments/" + java.util.UUID.randomUUID() + "/status")
                        .header(PipelineTokenFilter.HEADER, "test-pipeline-token-do-not-use-in-prod"))
                .andExpect(status().isNotFound());
    }

    @Test
    void jwtAllowsAuthenticatedAccess() throws Exception {
        RegisterRequest reg = new RegisterRequest();
        reg.setUsername("secuser");
        reg.setEmail("secuser@example.com");
        reg.setPassword("password123");
        String token = authService.register(reg).getToken();

        mockMvc.perform(get("/api/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.username").value("secuser"));
    }
}
