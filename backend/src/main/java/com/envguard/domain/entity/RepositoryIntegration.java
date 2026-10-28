package com.envguard.domain.entity;

import com.envguard.domain.enums.RepoProvider;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "repository_integrations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositoryIntegration {

    @Id
    @UuidGenerator
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private RepoProvider provider;

    @Column(name = "repo_url", nullable = false, length = 512)
    private String repoUrl;

    @Column(name = "repo_owner", nullable = false, length = 128)
    private String repoOwner;

    @Column(name = "repo_name", nullable = false, length = 128)
    private String repoName;

    /** Reference to secret store — never plaintext webhook secret. */
    @Column(name = "webhook_secret_ref", length = 512)
    private String webhookSecretRef;

    @Column(name = "default_branch", nullable = false, length = 128)
    @Builder.Default
    private String defaultBranch = "main";

    @Column(name = "external_repo_id", length = 128)
    private String externalRepoId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
