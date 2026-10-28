package com.envguard.repository;

import com.envguard.domain.entity.ApprovalRecord;
import com.envguard.domain.enums.ApprovalDecision;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApprovalRecordRepository extends JpaRepository<ApprovalRecord, Long> {
    List<ApprovalRecord> findByDeploymentRequestId(UUID deploymentRequestId);

    long countByDeploymentRequestIdAndDecision(UUID deploymentRequestId, ApprovalDecision decision);

    boolean existsByDeploymentRequestIdAndApprover_IdAndDecision(
            UUID deploymentRequestId, UUID approverId, ApprovalDecision decision);
}
