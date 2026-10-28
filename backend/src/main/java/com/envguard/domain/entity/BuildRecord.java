package com.envguard.domain.entity;

import com.envguard.domain.enums.BuildStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "build_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BuildRecord {

    @Id
    @UuidGenerator
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "commit_sha", nullable = false, length = 64)
    private String commitSha;

    @Column(nullable = false, length = 128)
    private String branch;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private BuildStatus status;

    @Column(name = "artifact_digest", length = 128)
    private String artifactDigest;

    @Column(name = "artifact_url", length = 1024)
    private String artifactUrl;

    @Lob
    @Column(name = "logs_redacted")
    private String logsRedacted;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Column(name = "pipeline_run_id", nullable = false, unique = true, length = 128)
    private String pipelineRunId;
}
