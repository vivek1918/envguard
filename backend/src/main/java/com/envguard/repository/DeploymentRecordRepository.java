package com.envguard.repository;

import com.envguard.domain.entity.DeploymentRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface DeploymentRecordRepository extends JpaRepository<DeploymentRecord, UUID> {
    List<DeploymentRecord> findByDeploymentRequestId(UUID deploymentRequestId);
}
