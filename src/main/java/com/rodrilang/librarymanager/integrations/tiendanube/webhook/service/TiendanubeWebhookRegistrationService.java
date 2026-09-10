package com.rodrilang.librarymanager.integrations.tiendanube.webhook.service;

import com.rodrilang.librarymanager.integrations.tiendanube.client.TiendanubeClient;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeWebhookResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;

@Slf4j
@Service
public class TiendanubeWebhookRegistrationService {

    private static final List<String> REQUIRED_EVENTS = List.of(
            "order/created",
            "order/paid",
            "order/cancelled"
    );

    private final TiendanubeClient client;
    private final String webhookUrl;

    public TiendanubeWebhookRegistrationService(
            TiendanubeClient client,
            @Value("${tiendanube.webhook-url:}") String webhookUrl
    ) {
        this.client = client;
        this.webhookUrl = webhookUrl;
    }

    public void ensureRegistered(Long storeId) {
        String targetUrl = normalizeAndValidateUrl(webhookUrl);
        List<TiendanubeWebhookResponse> current = client.getWebhooks(storeId);

        for (String event : REQUIRED_EVENTS) {
            if (isRegistered(current, event, targetUrl)) {
                continue;
            }

            client.createWebhook(storeId, event, targetUrl);
            log.info("Tiendanube webhook registered. storeId={} event={} url={}", storeId, event, targetUrl);
        }
    }

    private boolean isRegistered(List<TiendanubeWebhookResponse> current, String event, String targetUrl) {
        return current.stream().anyMatch(webhook ->
                webhook != null
                        && event.equals(webhook.event())
                        && targetUrl.equals(normalizeUrl(webhook.url()))
        );
    }

    private String normalizeAndValidateUrl(String value) {
        String normalized = normalizeUrl(value);
        if (normalized == null) {
            throw new IllegalStateException("tiendanube.webhook-url no está configurado");
        }

        URI uri;
        try {
            uri = URI.create(normalized);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("tiendanube.webhook-url no es una URL válida", exception);
        }

        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw new IllegalStateException("tiendanube.webhook-url debe ser una URL HTTPS pública");
        }

        return normalized;
    }

    private String normalizeUrl(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
