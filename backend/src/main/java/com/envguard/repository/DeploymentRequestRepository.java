package com.envguard.repository;

import com.envguard.domain.entity.DeploymentRequest;
import com.envguard.domain.enums.DeploymentRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeploymentRequestRepository extends JpaRepository<DeploymentRequest, UUID> {
    Optional<DeploymentRequest> findByIdempotencyKey(String idempotencyKey);

    List<DeploymentRequest> findByStatus(DeploymentRequestStatus status);

    List<DeploymentRequest> findByStatusIn(List<DeploymentRequestStatus> statuses);

    @Query("SELECT d FROM DeploymentRequest d WHERE d.status = :status AND d.expiresAt IS NOT NULL AND d.expiresAt < :now")
    List<DeploymentRequest> findExpired(@Param("status") DeploymentRequestStatus status, @Param("now") Instant now);

    List<DeploymentRequest> findByProjectIdOrderByCreatedAtDesc(UUID projectId);

    long countByStatus(DeploymentRequestStatus status);
}
