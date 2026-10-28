package com.envguard.repository;

import com.envguard.domain.entity.Project;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    Optional<Project> findByName(String name);
    boolean existsByName(String name);
    List<Project> findByOwnerId(UUID ownerId);
}
