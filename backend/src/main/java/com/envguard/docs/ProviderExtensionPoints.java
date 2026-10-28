package com.envguard.docs;

/**
 * Extension points for repository and pipeline providers.
 *
 * <h2>GitHub</h2>
 * Implemented: {@link com.envguard.provider.GitHubRepositoryProvider} (HMAC webhook validation + push parse).
 *
 * <h2>Bitbucket</h2>
 * Stub: {@link com.envguard.provider.BitbucketRepositoryProvider}. Implement HMAC validation and push payload mapping.
 *
 * <h2>Jenkins</h2>
 * Stub: {@link com.envguard.provider.JenkinsPipelineIntegration}. Prefer calling EnvGuard pipeline REST APIs
 * with {@code X-EnvGuard-Pipeline-Token} rather than embedding secrets in Jenkinsfiles.
 *
 * <h2>Adding a provider</h2>
 * <ol>
 *   <li>Implement {@link com.envguard.provider.RepoProviderClient} or {@link com.envguard.provider.PipelineIntegration}.</li>
 *   <li>Register as a Spring {@code @Component}.</li>
 *   <li>Add enum value to {@link com.envguard.domain.enums.RepoProvider} if storing integrations.</li>
 * </ol>
 */
public final class ProviderExtensionPoints {
    private ProviderExtensionPoints() {
    }
}
