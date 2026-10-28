package com.envguard.repository;

import com.envguard.domain.entity.BuildRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BuildRecordRepository extends JpaRepository<BuildRecord, UUID> {
    Optional<BuildRecord> findByPipelineRunId(String pipelineRunId);
    List<BuildRecord> findByProjectIdOrderByStartedAtDesc(UUID projectId);
}
