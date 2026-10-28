package com.envguard.domain.entity;

import com.envguard.domain.enums.RevisionApprovalStatus;
import com.envguard.domain.enums.ValidationStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Immutable once persisted. Application code must never update config_yaml or checksum.
 */
@Entity
@Table(name = "configuration_revisions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfigurationRevision {

    @Id
    @UuidGenerator
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false, updatable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "environment_id", nullable = false, updatable = false)
    private Environment environment;

    @Column(name = "source_commit_sha", nullable = false, length = 64, updatable = false)
    private String sourceCommitSha;

    @Column(nullable = false, length = 64, updatable = false)
    private String checksum;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_user_id", updatable = false)
    private User authorUser;

    @Column(name = "author_identity", nullable = false, updatable = false)
    private String authorIdentity;

    @Lob
    @Column(name = "config_yaml", nullable = false, updatable = false)
    private String configYaml;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false, length = 32, updatable = false)
    private ValidationStatus validationStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", nullable = false, length = 32)
    private RevisionApprovalStatus approvalStatus;

    @Column(name = "artifact_digest", length = 128, updatable = false)
    private String artifactDigest;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @OneToMany(mappedBy = "revision", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<ConfigurationVariable> variables = new ArrayList<>();

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
