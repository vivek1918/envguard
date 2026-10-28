package com.envguard.provider;

import org.springframework.stereotype.Component;

/**
 * Documented stub for Jenkins integration.
 *
 * <p>Suggested design:
 * <ul>
 *   <li>Jenkins Notifier / generic webhook posts build metadata to EnvGuard.</li>
 *   <li>Authenticate with {@code X-EnvGuard-Pipeline-Token} (same as GitHub Actions).</li>
 *   <li>Map {@code BUILD_URL} → {@code pipelineRunId}, {@code GIT_COMMIT} → {@code commitSha}.</li>
 *   <li>Compute artifact digest in the job (sha256 of tarball) before requesting deployment.</li>
 * </ul>
 */
@Component
public class JenkinsPipelineIntegration implements PipelineIntegration {

    @Override
    public String getSystemName() {
        return "JENKINS";
    }
}
