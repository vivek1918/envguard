package com.envguard.repository;

import com.envguard.domain.entity.RepositoryIntegration;
import com.envguard.domain.enums.RepoProvider;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositoryIntegrationRepository extends JpaRepository<RepositoryIntegration, UUID> {
    List<RepositoryIntegration> findByProjectId(UUID projectId);
    Optional<RepositoryIntegration> findByProviderAndRepoOwnerAndRepoName(
            RepoProvider provider, String repoOwner, String repoName);
}
