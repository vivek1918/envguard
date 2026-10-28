package com.envguard.service;

import com.envguard.domain.entity.WebhookEvent;
import com.envguard.exception.ApiException;
import com.envguard.provider.ParsedWebhookEvent;
import com.envguard.provider.RepositoryProvider;
import com.envguard.provider.RepositoryProviderRegistry;
import com.envguard.repository.WebhookEventRepository;
import com.envguard.util.ChecksumUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebhookService {

    private final RepositoryProviderRegistry providerRegistry;
    private final WebhookEventRepository webhookEventRepository;
    private final AuditService auditService;

    @Transactional
    public Map<String, Object> handle(String providerName, String signatureHeader, String payload, String secret) {
        return handle(providerName, signatureHeader, null, payload, secret);
    }

    @Transactional
    public Map<String, Object> handle(String providerName, String signatureHeader, String deliveryIdHeader,
                                     String payload, String secret) {
        RepositoryProvider provider = providerRegistry.get(providerName);
        if (StringUtils.hasText(secret) && !provider.validateWebhook(signatureHeader, payload, secret)) {
            throw ApiException.unauthorized("Invalid webhook signature");
        }

        ParsedWebhookEvent event = provider.parseWebhook(payload);
        String deliveryId = StringUtils.hasText(deliveryIdHeader)
                ? deliveryIdHeader
                : event.getDeliveryId();
        if (!StringUtils.hasText(deliveryId)) {
            deliveryId = ChecksumUtil.sha256Hex(payload).substring(0, 32);
        }

        if (webhookEventRepository.existsByDeliveryId(deliveryId)) {
            Map<String, Object> dup = new LinkedHashMap<>();
            dup.put("status", "DUPLICATE");
            dup.put("duplicate", true);
            dup.put("deliveryId", deliveryId);
            return dup;
        }

        WebhookEvent stored = WebhookEvent.builder()
                .provider(provider.getProviderName().toUpperCase())
                .deliveryId(deliveryId)
                .payloadHash(ChecksumUtil.sha256Hex(payload))
                .build();
        webhookEventRepository.save(stored);

        auditService.record(null, null, null, "WEBHOOK_RECEIVED", "WebhookEvent", stored.getId().toString(),
                Map.of("provider", provider.getProviderName(), "deliveryId", deliveryId,
                        "commitSha", event.getCommitSha() == null ? "" : event.getCommitSha()));

        Map<String, Object> accepted = new LinkedHashMap<>();
        accepted.put("status", "ACCEPTED");
        accepted.put("duplicate", false);
        accepted.put("deliveryId", deliveryId);
        accepted.put("commitSha", event.getCommitSha() == null ? "" : event.getCommitSha());
        accepted.put("branch", event.getBranch() == null ? "" : event.getBranch());
        return accepted;
    }
}
