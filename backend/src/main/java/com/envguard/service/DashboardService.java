package com.envguard.service;

import com.envguard.domain.enums.DeploymentRequestStatus;
import com.envguard.repository.AuditEventRepository;
import com.envguard.repository.DeploymentRequestRepository;
import com.envguard.repository.ProjectRepository;
import com.envguard.repository.UserRepository;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final DeploymentRequestRepository deploymentRequestRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final AuditEventRepository auditEventRepository;

    @Data
    @Builder
    public static class Stats {
        private long projects;
        private long users;
        private long pendingApprovals;
        private long approved;
        private long deployed;
        private long failed;
        private long rejected;
        private long timedOut;
        private long auditEvents;
        private Map<String, Long> byStatus;
    }

    @Transactional(readOnly = true)
    public Stats stats() {
        Map<String, Long> byStatus = new LinkedHashMap<>();
        for (DeploymentRequestStatus status : DeploymentRequestStatus.values()) {
            byStatus.put(status.name(), deploymentRequestRepository.countByStatus(status));
        }
        return Stats.builder()
                .projects(projectRepository.count())
                .users(userRepository.count())
                .pendingApprovals(byStatus.getOrDefault(DeploymentRequestStatus.READY_FOR_APPROVAL.name(), 0L))
                .approved(byStatus.getOrDefault(DeploymentRequestStatus.APPROVED.name(), 0L))
                .deployed(byStatus.getOrDefault(DeploymentRequestStatus.DEPLOYED.name(), 0L))
                .failed(byStatus.getOrDefault(DeploymentRequestStatus.FAILED.name(), 0L))
                .rejected(byStatus.getOrDefault(DeploymentRequestStatus.REJECTED.name(), 0L))
                .timedOut(byStatus.getOrDefault(DeploymentRequestStatus.TIMED_OUT.name(), 0L))
                .auditEvents(auditEventRepository.count())
                .byStatus(byStatus)
                .build();
    }
}
