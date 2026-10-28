package com.envguard.repository;

import com.envguard.domain.entity.ConfigurationRevision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ConfigurationRevisionRepository extends JpaRepository<ConfigurationRevision, UUID> {
    List<ConfigurationRevision> findByProjectIdAndEnvironmentIdOrderByCreatedAtDesc(
            UUID projectId, UUID environmentId);
    Optional<ConfigurationRevision> findFirstByProjectIdAndEnvironmentIdOrderByCreatedAtDesc(
            UUID projectId, UUID environmentId);
}
