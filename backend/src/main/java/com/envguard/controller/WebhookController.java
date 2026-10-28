package com.envguard.controller;

import com.envguard.dto.response.ApiResponse;
import com.envguard.service.WebhookService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/webhooks")
@RequiredArgsConstructor
public class WebhookController {

    private final WebhookService webhookService;

    @PostMapping("/{provider}")
    public ApiResponse<Map<String, Object>> receive(
            @PathVariable String provider,
            @RequestHeader(value = "X-Hub-Signature-256", required = false) String githubSig,
            @RequestHeader(value = "X-Hub-Signature", required = false) String hubSig,
            @RequestHeader(value = "X-GitHub-Delivery", required = false) String githubDelivery,
            @RequestHeader(value = "X-Request-UUID", required = false) String requestUuid,
            @RequestHeader(value = "X-EnvGuard-Webhook-Secret", required = false) String secret,
            @RequestBody String payload) {
        String signature = githubSig != null ? githubSig : hubSig;
        String deliveryId = githubDelivery != null ? githubDelivery : requestUuid;
        return ApiResponse.ok(webhookService.handle(provider, signature, deliveryId, payload, secret));
    }
}
