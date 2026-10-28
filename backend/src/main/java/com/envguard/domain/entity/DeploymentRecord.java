package com.envguard.domain.entity;

import com.envguard.domain.enums.DeploymentRecordStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "deployment_records")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeploymentRecord {

    @Id
    @UuidGenerator
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "deployment_request_id", nullable = false)
    private DeploymentRequest deploymentRequest;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeploymentRecordStatus status;

    @Column(name = "target_host")
    private String targetHost;

    @Column(name = "health_status", length = 32)
    private String healthStatus;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "error_message_redacted", length = 2048)
    private String errorMessageRedacted;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
