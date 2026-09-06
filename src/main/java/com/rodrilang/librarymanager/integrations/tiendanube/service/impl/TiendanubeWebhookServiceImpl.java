package com.rodrilang.librarymanager.integrations.tiendanube.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeWebhookRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeWebhookService;
import com.rodrilang.librarymanager.integrations.tiendanube.webhook.enums.TiendanubeWebhookEventStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.webhook.repository.TiendanubeWebhookEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeWebhookServiceImpl implements TiendanubeWebhookService {

    private static final Set<String> PROCESSABLE_EVENTS = Set.of("order/paid", "order/cancelled");

    private final ObjectMapper objectMapper;
    private final TiendanubeStoreRepository storeRepository;
    private final TiendanubeWebhookEventRepository webhookEventRepository;

    @Value("${tiendanube.webhook-inbox.max-attempts:12}")
    private int maxAttempts;

    @Override
    @Transactional
    public void accept(String payload) {
        TiendanubeWebhookRequest request = parse(payload);
        validateBasePayload(request);

        Instant now = Instant.now();
        Long internalStoreId = storeRepository.findByStoreId(request.storeId())
                .map(TiendanubeStore::getId)
                .orElse(null);

        TiendanubeWebhookEventStatus status = resolveInitialStatus(request);
        String errorType = null;
        String errorMessage = null;

        if (status == TiendanubeWebhookEventStatus.FAILED) {
            errorType = "INVALID_PAYLOAD";
            errorMessage = "El evento " + request.event() + " no incluye el identificador del recurso";
        }

        Long eventId = webhookEventRepository.insert(
                internalStoreId,
                request.storeId(),
                request.event(),
                request.id(),
                payload,
                status,
                Math.max(1, maxAttempts),
                now,
                errorType,
                errorMessage
        );

        log.info(
                "Webhook Tiendanube persisted. eventId={} storeId={} event={} resourceId={} status={}",
                eventId, request.storeId(), request.event(), request.id(), status
        );
    }

    private TiendanubeWebhookRequest parse(String payload) {
        if (payload == null || payload.isBlank()) {
            throw new BusinessException("El webhook de Tiendanube no contiene payload");
        }

        try {
            return objectMapper.readValue(payload, TiendanubeWebhookRequest.class);
        } catch (JsonProcessingException exception) {
            throw new BusinessException("El webhook de Tiendanube contiene JSON inválido");
        }
    }

    private void validateBasePayload(TiendanubeWebhookRequest request) {
        if (request == null || request.storeId() == null) {
            throw new BusinessException("El webhook de Tiendanube no contiene store_id");
        }

        if (request.event() == null || request.event().isBlank()) {
            throw new BusinessException("El webhook de Tiendanube no contiene event");
        }
    }

    private TiendanubeWebhookEventStatus resolveInitialStatus(TiendanubeWebhookRequest request) {
        if (!PROCESSABLE_EVENTS.contains(request.event())) {
            return TiendanubeWebhookEventStatus.IGNORED;
        }

        if (request.id() == null) {
            return TiendanubeWebhookEventStatus.FAILED;
        }

        return TiendanubeWebhookEventStatus.PENDING;
    }
}
