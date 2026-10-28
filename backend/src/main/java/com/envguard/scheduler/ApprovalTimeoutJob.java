package com.envguard.scheduler;

import com.envguard.domain.entity.DeploymentRequest;
import com.envguard.domain.enums.DeploymentRequestStatus;
import com.envguard.repository.DeploymentRequestRepository;
import com.envguard.service.DeploymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class ApprovalTimeoutJob {

    private final DeploymentRequestRepository deploymentRequestRepository;
    private final DeploymentService deploymentService;

    @Scheduled(fixedDelayString = "${envguard.approval.timeout-check-ms:60000}")
    @Transactional
    public void markTimedOut() {
        Instant now = Instant.now();
        List<DeploymentRequest> expired = deploymentRequestRepository.findExpired(
                DeploymentRequestStatus.READY_FOR_APPROVAL, now);
        for (DeploymentRequest req : expired) {
            log.info("Marking deployment request {} as TIMED_OUT", req.getId());
            deploymentService.expireIfNeeded(req, now);
        }
    }
}
