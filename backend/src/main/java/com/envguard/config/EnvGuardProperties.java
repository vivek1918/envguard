package com.envguard.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "envguard")
public class EnvGuardProperties {

    private Jwt jwt = new Jwt();
    private Pipeline pipeline = new Pipeline();
    private Cors cors = new Cors();
    private Aws aws = new Aws();
    private Approval approval = new Approval();
    private RateLimit rateLimit = new RateLimit();
    private Bootstrap bootstrap = new Bootstrap();

    @Data
    public static class Jwt {
        private String secret;
        private long expirationMs = 86_400_000L;
    }

    @Data
    public static class Pipeline {
        private String token;
    }

    @Data
    public static class Cors {
        private String allowedOrigins = "http://localhost:4200";
    }

    @Data
    public static class Aws {
        private boolean enabled = false;
        private String region = "us-east-1";
    }

    @Data
    public static class Approval {
        private int defaultTimeoutMinutes = 60;
        private boolean rejectSelfApprovalProduction = true;
    }

    @Data
    public static class RateLimit {
        private int loginMaxAttempts = 10;
        private int loginWindowSeconds = 60;
    }

    @Data
    public static class Bootstrap {
        private String adminUser;
        private String adminPassword;
        private String adminEmail;
    }
}
