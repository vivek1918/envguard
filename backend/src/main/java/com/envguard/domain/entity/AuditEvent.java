package com.envguard.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit trail. Application must never update or delete rows.
 */
@Entity
@Table(name = "audit_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditEvent {

    @Id
    @UuidGenerator
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @Column(name = "project_id", length = 36, updatable = false)
    private UUID projectId;

    @Column(name = "user_id", length = 36, updatable = false)
    private UUID userId;

    @Column(name = "environment_id", length = 36, updatable = false)
    private UUID environmentId;

    @Column(nullable = false, length = 64, updatable = false)
    private String action;

    @Column(name = "entity_type", nullable = false, length = 64, updatable = false)
    private String entityType;

    @Column(name = "entity_id", length = 64, updatable = false)
    private String entityId;

    /** JSON without secrets — always redacted before persist. */
    @Lob
    @Column(name = "details_json", updatable = false)
    private String detailsJson;

    @Column(name = "correlation_id", length = 64, updatable = false)
    private String correlationId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }
}
