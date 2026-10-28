package com.envguard.domain.entity;

import com.envguard.domain.enums.EnvironmentName;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "environments",
        uniqueConstraints = @UniqueConstraint(columnNames = {"project_id", "name"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Environment {

    @Id
    @UuidGenerator
    @Column(length = 36, nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private EnvironmentName name;

    @Column(name = "requires_second_approver", nullable = false)
    @Builder.Default
    private boolean requiresSecondApprover = false;

    @Column(name = "approval_timeout_minutes", nullable = false)
    @Builder.Default
    private int approvalTimeoutMinutes = 60;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "environment_approvers",
            joinColumns = @JoinColumn(name = "environment_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @Builder.Default
    private Set<User> approvers = new HashSet<>();
}
