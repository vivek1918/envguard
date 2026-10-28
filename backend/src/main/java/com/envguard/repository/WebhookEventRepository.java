package com.envguard.repository;

import com.envguard.domain.entity.WebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface WebhookEventRepository extends JpaRepository<WebhookEvent, UUID> {
    boolean existsByDeliveryId(String deliveryId);
    Optional<WebhookEvent> findByDeliveryId(String deliveryId);
}
