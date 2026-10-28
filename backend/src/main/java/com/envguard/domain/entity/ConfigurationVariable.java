package com.envguard.domain.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Stores plaintext non-secret values and secret *references* only — never secret payloads.
 */
@Entity
@Table(name = "configuration_variables",
        uniqueConstraints = @UniqueConstraint(columnNames = {"revision_id", "var_name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConfigurationVariable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "revision_id", nullable = false, updatable = false)
    private ConfigurationRevision revision;

    @Column(name = "var_name", nullable = false, length = 128, updatable = false)
    private String varName;

    /** Non-secret value only; null when isSecretRef=true. */
    @Column(name = "var_value", length = 4096, updatable = false)
    private String varValue;

    @Column(name = "is_secret_ref", nullable = false, updatable = false)
    @Builder.Default
    private boolean secretRef = false;

    @Column(name = "secret_reference", length = 512, updatable = false)
    private String secretReference;

    @Column(name = "secret_version", length = 128, updatable = false)
    private String secretVersion;
}
