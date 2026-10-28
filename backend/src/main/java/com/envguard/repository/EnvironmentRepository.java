package com.envguard.repository;

import com.envguard.domain.entity.Environment;
import com.envguard.domain.enums.EnvironmentName;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EnvironmentRepository extends JpaRepository<Environment, UUID> {
    List<Environment> findByProjectId(UUID projectId);
    Optional<Environment> findByProjectIdAndName(UUID projectId, EnvironmentName name);
}
