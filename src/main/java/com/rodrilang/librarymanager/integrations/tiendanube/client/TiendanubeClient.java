package com.rodrilang.librarymanager.integrations.tiendanube.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rodrilang.librarymanager.integrations.tiendanube.config.TiendanubeProperties;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeCreateWebhookRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeUpdateStockRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeOrderResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductVariantResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeTokenResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeWebhookResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeClient {

    private static final String USER_AGENT_VALUE = "Library Manager (Rodrigolang90@gmail.com)";
    private static final String BEARER_PREFIX = "Bearer ";

    private final TiendanubeStoreRepository storeRepository;
    private final TiendanubeProperties properties;
    private final RestClient tiendanubeRestClient;
    private final ObjectMapper objectMapper;

    public TiendanubeOrderResponse getOrder(Long storeId, Long orderId) {
        TiendanubeStore store = getActiveStore(storeId);

        return tiendanubeRestClient.get()
                .uri(properties.apiUrl() + "/{storeId}/orders/{orderId}", storeId, orderId)
                .header(HttpHeaders.AUTHORIZATION, buildAuthorizationHeader(store))
                .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(TiendanubeOrderResponse.class);
    }

    public TiendanubeProductResponse getProduct(Long storeId, Long productId) {
        TiendanubeStore store = getActiveStore(storeId);

        return tiendanubeRestClient.get()
                .uri(properties.apiUrl() + "/{storeId}/products/{productId}", storeId, productId)
                .header(HttpHeaders.AUTHORIZATION, buildAuthorizationHeader(store))
                .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(TiendanubeProductResponse.class);
    }

    public TiendanubeProductVariantResponse updateStock(
            Long storeId,
            Long productId,
            Long variantId,
            Integer stock
    ) {
        TiendanubeStore store = getActiveStore(storeId);

        TiendanubeUpdateStockRequest request = new TiendanubeUpdateStockRequest(true, stock);

        return tiendanubeRestClient.put()
                .uri(properties.apiUrl() + "/{storeId}/products/{productId}/variants/{variantId}",
                        storeId,
                        productId,
                        variantId
                )
                .header(HttpHeaders.AUTHORIZATION, buildAuthorizationHeader(store))
                .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(TiendanubeProductVariantResponse.class);
    }

    public TiendanubeWebhookResponse createWebhook(
            Long storeId,
            String event,
            String url
    ) {
        TiendanubeStore store = getActiveStore(storeId);

        TiendanubeCreateWebhookRequest request = new TiendanubeCreateWebhookRequest(event, url);

        return tiendanubeRestClient.post()
                .uri(properties.apiUrl() + "/{storeId}/webhooks", storeId)
                .header(HttpHeaders.AUTHORIZATION, buildAuthorizationHeader(store))
                .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .body(TiendanubeWebhookResponse.class);
    }

    public TiendanubeTokenResponse exchangeCodeForToken(String code) {
        validateAuthorizationCode(code);

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("code", code);
        form.add("grant_type", "authorization_code");

        try {
            ResponseEntity<String> response = tiendanubeRestClient.post()
                    .uri(properties.tokenUrl())
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .toEntity(String.class);

            return parseTokenResponse(response);

        } catch (RestClientResponseException exception) {
            log.error("Tiendanube rechazó el intercambio OAuth. status={}", exception.getStatusCode());

            throw new IllegalStateException("No se pudo completar la autenticación con Tiendanube.", exception);
        }
    }

    private TiendanubeTokenResponse parseTokenResponse(ResponseEntity<String> response) {
        String body = response.getBody();

        if (body == null || body.isBlank()) {
            throw new IllegalStateException("Tiendanube devolvió una respuesta OAuth vacía.");
        }

        try {
            TiendanubeTokenResponse tokenResponse = objectMapper.readValue(body, TiendanubeTokenResponse.class);

            validateTokenResponse(tokenResponse);

            log.info("Autenticación con Tiendanube completada para storeId={}", tokenResponse.userId());

            return tokenResponse;

        } catch (JsonProcessingException exception) {
            log.error("No se pudo interpretar la respuesta OAuth de Tiendanube. status={}, contentType={}",
                    response.getStatusCode(),
                    response.getHeaders().getContentType()
            );

            throw new IllegalStateException("Tiendanube devolvió una respuesta OAuth inválida.", exception);
        }
    }

    private void validateAuthorizationCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("El código de autorización de Tiendanube es obligatorio.");
        }
    }

    private void validateTokenResponse(
            TiendanubeTokenResponse response
    ) {
        if (response == null) {
            throw new IllegalStateException("Tiendanube devolvió una respuesta OAuth inválida.");
        }

        if (response.accessToken() == null || response.accessToken().isBlank()) {
            throw new IllegalStateException("Tiendanube no devolvió el access token.");
        }

        if (response.userId() == null) {
            throw new IllegalStateException("Tiendanube no devolvió el identificador de la tienda.");
        }

        if (response.tokenType() == null || response.tokenType().isBlank()) {
            throw new IllegalStateException("Tiendanube no devolvió el tipo de token.");
        }
    }

    private String buildAuthorizationHeader(TiendanubeStore store) {
        return BEARER_PREFIX + store.getAccessToken();
    }

    private TiendanubeStore getActiveStore(Long storeId) {
        return storeRepository.findByStoreIdAndActiveTrue(storeId)
                .orElseThrow(() -> new EntityNotFoundException("Tienda Tiendanube no conectada"));
    }

}