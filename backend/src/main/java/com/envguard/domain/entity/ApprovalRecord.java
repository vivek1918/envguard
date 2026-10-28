package com.envguard.domain.entity;

import com.envguard.domain.enums.ApprovalDecision;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Immutable approval evidence bound to exact revision checksum + artifact digest.
 */
@Entity
@Table(name = "approval_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApprovalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deployment_request_id", nullable = false, updatable = false)
    private DeploymentRequest deploymentRequest;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "approver_user_id", nullable = false, updatable = false)
    private User approver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private ApprovalDecision decision;

    @Column(length = 1024, updatable = false)
    private String comment;

    @Column(name = "revision_id", nullable = false, length = 36, updatable = false)
    private UUID revisionId;

    @Column(nullable = false, length = 64, updatable = false)
    private String checksum;

    @Column(name = "commit_sha", nullable = false, length = 64, updatable = false)
    private String commitSha;

    @Column(name = "artifact_digest", nullable = false, length = 128, updatable = false)
    private String artifactDigest;

    @Column(name = "environment_id", nullable = false, length = 36, updatable = false)
    private UUID environmentId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
