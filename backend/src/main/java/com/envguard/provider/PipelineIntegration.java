package com.envguard.provider;

/**
 * Extension point for CI/CD systems beyond the EnvGuard pipeline token API.
 *
 * <p><b>Jenkins:</b> implement a webhook/controller that maps Jenkins build notifications
 * to {@code POST /api/pipeline/builds} and {@code POST /api/pipeline/deployments} using the
 * shared {@code X-EnvGuard-Pipeline-Token}. Prefer Jenkins Credentials Binding for the token;
 * never embed tokens in job scripts committed to git.
 *
 * <p><b>GitHub Actions / Bitbucket Pipelines:</b> call the same pipeline REST endpoints from
 * workflow steps after artifact digest is known.
 *
 * <p>This interface is intentionally minimal so adapters can remain outside the core domain.
 */
public interface PipelineIntegration {

    String getSystemName();

    /**
     * Optional: translate a native CI payload into EnvGuard pipeline calls.
     * Default stubs throw until a concrete adapter is enabled.
     */
    default void handleNativeEvent(String payload) {
        throw new UnsupportedOperationException(getSystemName() + " adapter is not enabled");
    }
}
