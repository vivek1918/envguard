package com.envguard.provider;

/**
 * Abstraction for VCS providers (GitHub, Bitbucket, …).
 * Implementations live as {@link RepositoryProvider} beans registered in {@link RepositoryProviderRegistry}.
 *
 * <p>Extension: implement this interface (or {@link RepositoryProvider}), annotate with {@code @Component},
 * and return a unique {@link #getProviderName()}.
 */
public interface RepoProviderClient extends RepositoryProvider {
}
