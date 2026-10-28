package com.envguard.service;

import com.envguard.domain.entity.AuditEvent;
import com.envguard.repository.AuditEventRepository;
import com.envguard.security.CorrelationIdFilter;
import com.envguard.util.SecretRedactor;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuditService {

    private final AuditEventRepository auditEventRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public void record(UUID projectId, UUID userId, UUID environmentId,
                       String action, String entityType, String entityId,
                       Map<String, Object> details) {
        String json = null;
        try {
            Map<String, Object> safe = SecretRedactor.redactMap(details == null ? Map.of() : details);
            json = objectMapper.writeValueAsString(safe);
        } catch (Exception e) {
            json = "{\"error\":\"failed_to_serialize\"}";
        }

        AuditEvent event = AuditEvent.builder()
                .projectId(projectId)
                .userId(userId)
                .environmentId(environmentId)
                .action(action)
                .entityType(entityType)
                .entityId(entityId)
                .detailsJson(json)
                .correlationId(CorrelationIdFilter.current())
                .build();
        auditEventRepository.save(event);
    }

    @Transactional(readOnly = true)
    public Page<AuditEvent> search(UUID projectId, UUID userId, String action, Pageable pageable) {
        Specification<AuditEvent> spec = (root, q, cb) -> cb.conjunction();
        if (projectId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("projectId"), projectId));
        }
        if (userId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("userId"), userId));
        }
        if (action != null && !action.isBlank()) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("action"), action));
        }
        return auditEventRepository.findAll(spec, pageable);
    }
}
