package com.rodrilang.librarymanager.integrations.tiendanube.client;

import com.rodrilang.librarymanager.integrations.tiendanube.config.TiendanubeProperties;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeCreateWebhookRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeOrderResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductVariantResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeTokenResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeUpdateStockRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeWebhookResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeClient {

    private final TiendanubeStoreRepository storeRepository;
    private final TiendanubeProperties properties;
    private final RestClient tiendanubeRestClient;

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String USER_AGENT_HEADER = "User-Agent";
    private static final String USER_AGENT_VALUE = "Library Manager (Rodrigolang90@gmail.com)";
    private static final String BEARER_PREFIX = "Bearer ";

    public TiendanubeOrderResponse getOrder(Long storeId, Long orderId) {

        TiendanubeStore store = getActiveStore(storeId);

        return tiendanubeRestClient.get()
                .uri(properties.apiUrl() + "/{storeId}/orders/{orderId}", storeId, orderId)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + store.getAccessToken())
                .header(USER_AGENT_HEADER, USER_AGENT_VALUE)
                .retrieve()
                .body(TiendanubeOrderResponse.class);
    }

    public TiendanubeProductResponse getProduct(Long storeId, Long productId) {
        TiendanubeStore store = getActiveStore(storeId);

        return tiendanubeRestClient.get()
                .uri(properties.apiUrl() + "/{storeId}/products/{productId}", storeId, productId)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + store.getAccessToken())
                .header(USER_AGENT_HEADER, USER_AGENT_VALUE)
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

        TiendanubeUpdateStockRequest request = new TiendanubeUpdateStockRequest(
                true,
                stock
        );

        return tiendanubeRestClient.put()
                .uri(properties.apiUrl() + "/{storeId}/products/{productId}/variants/{variantId}",
                        storeId,
                        productId,
                        variantId
                )
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + store.getAccessToken())
                .header(USER_AGENT_HEADER, USER_AGENT_VALUE)
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

        TiendanubeCreateWebhookRequest request = new TiendanubeCreateWebhookRequest(
                event,
                url
        );

        return tiendanubeRestClient.post()
                .uri(properties.apiUrl() + "/{storeId}/webhooks", storeId)
                .header(AUTHORIZATION_HEADER, BEARER_PREFIX + store.getAccessToken())
                .header(USER_AGENT_HEADER, USER_AGENT_VALUE)
                .body(request)
                .retrieve()
                .body(TiendanubeWebhookResponse.class);
    }

    public TiendanubeTokenResponse exchangeCodeForToken(String code) {

        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        form.add("code", code);
        form.add("grant_type", "authorization_code");

        return tiendanubeRestClient.post()
                .uri(properties.tokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(TiendanubeTokenResponse.class);
    }

    private TiendanubeStore getActiveStore(Long storeId) {
        return storeRepository.findByStoreIdAndActiveTrue(storeId)
                .orElseThrow(() -> new EntityNotFoundException("Tienda Tiendanube no conectada"));
    }

}