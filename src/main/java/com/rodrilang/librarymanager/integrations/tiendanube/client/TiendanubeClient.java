package com.rodrilang.librarymanager.integrations.tiendanube.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.config.TiendanubeProperties;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeCreateProductRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeCreateWebhookRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeUpdateStockRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeOrderResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductVariantResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeTokenResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeWebhookResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.exception.TiendanubeApiException;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeClient {

    private static final String USER_AGENT_VALUE = "Library Manager (Rodrigolang90@gmail.com)";
    private static final String BEARER_PREFIX = "Bearer ";

    private static final int PRODUCTS_PAGE_SIZE = 200;

    private final TiendanubeStoreRepository storeRepository;
    private final TiendanubeProperties properties;
    private final RestClient tiendanubeRestClient;
    private final ObjectMapper objectMapper;

    public TiendanubeProductResponse createProduct(Long storeId, TiendanubeCreateProductRequest request) {
        TiendanubeStore store = getActiveStore(storeId);

        try {
            return tiendanubeRestClient.post()
                    .uri(properties.apiUrl() + "/{storeId}/products", storeId)
                    .header(HttpHeaders.AUTHORIZATION, buildAuthorizationHeader(store))
                    .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(TiendanubeProductResponse.class);

        } catch (RestClientException exception) {
            throw buildApiException("crear producto", exception);
        }
    }

    public TiendanubeOrderResponse getOrder(Long storeId, Long orderId) {
        TiendanubeStore store = getActiveStore(storeId);
        try {
            return tiendanubeRestClient.get()
                    .uri(properties.apiUrl() + "/{storeId}/orders/{orderId}", storeId, orderId)
                    .header(HttpHeaders.AUTHORIZATION, buildAuthorizationHeader(store))
                    .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(TiendanubeOrderResponse.class);

        } catch (RestClientException exception) {
            throw buildApiException("obtener pedido", exception);
        }

    }

    public TiendanubeProductResponse getProduct(Long storeId, Long productId) {
        TiendanubeStore store = getActiveStore(storeId);

        try {
            return tiendanubeRestClient.get()
                    .uri(properties.apiUrl() + "/{storeId}/products/{productId}", storeId, productId)
                    .header(HttpHeaders.AUTHORIZATION, buildAuthorizationHeader(store))
                    .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(TiendanubeProductResponse.class);
        } catch (RestClientException exception) {
            throw buildApiException("obtener producto", exception);
        }
    }

    public List<TiendanubeProductResponse> getProducts(Long storeId) {
        TiendanubeStore store = getActiveStore(storeId);

        List<TiendanubeProductResponse> products = new ArrayList<>();
        int page = 1;

        while (true) {
            TiendanubeProductResponse[] currentPage = getProductsPage(
                    store,
                    storeId,
                    page,
                    PRODUCTS_PAGE_SIZE
            );

            if (currentPage == null || currentPage.length == 0) {
                break;
            }

            products.addAll(Arrays.asList(currentPage));

            if (currentPage.length < PRODUCTS_PAGE_SIZE) {
                break;
            }

            page++;
        }

        return products;
    }

    private TiendanubeProductResponse[] getProductsPage(
            TiendanubeStore store,
            Long storeId,
            int page,
            int perPage
    ) {
        try {
            return tiendanubeRestClient.get()
                    .uri(
                            properties.apiUrl()
                                    + "/{storeId}/products?page={page}&per_page={perPage}",
                            storeId,
                            page,
                            perPage
                    )
                    .header(HttpHeaders.AUTHORIZATION, buildAuthorizationHeader(store))
                    .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(TiendanubeProductResponse[].class);

        } catch (RestClientException exception) {
            throw buildApiException(
                    "obtener lista de productos",
                    exception
            );
        }
    }

    public TiendanubeProductVariantResponse updateStock(
            Long storeId,
            Long productId,
            Long variantId,
            Integer stock
    ) {
        TiendanubeStore store = getActiveStore(storeId);

        TiendanubeUpdateStockRequest request = new TiendanubeUpdateStockRequest(true, stock);
        try {
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

        } catch (RestClientException exception) {
            throw buildApiException("actualizar stock", exception);
        }
    }

    public TiendanubeWebhookResponse createWebhook(
            Long storeId,
            String event,
            String url
    ) {
        TiendanubeStore store = getActiveStore(storeId);

        TiendanubeCreateWebhookRequest request = new TiendanubeCreateWebhookRequest(event, url);
        try {
            return tiendanubeRestClient.post()
                    .uri(properties.apiUrl() + "/{storeId}/webhooks", storeId)
                    .header(HttpHeaders.AUTHORIZATION, buildAuthorizationHeader(store))
                    .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(TiendanubeWebhookResponse.class);
        } catch (RestClientException exception) {
            throw buildApiException("crear webhook", exception);
        }
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
        } catch (RestClientException exception) {
            throw buildApiException("autenticación con Tiendanube", exception);
        }
    }

    private TiendanubeTokenResponse parseTokenResponse(ResponseEntity<String> response) {
        String body = response.getBody();

        if (body == null || body.isBlank()) {
            throw new TiendanubeApiException(
                    "Tiendanube devolvió una respuesta de autenticación vacía"
            );
        }

        try {
            TiendanubeTokenResponse tokenResponse =
                    objectMapper.readValue(body, TiendanubeTokenResponse.class);

            validateTokenResponse(tokenResponse);

            log.info(
                    "Autenticación con Tiendanube completada para storeId={}",
                    tokenResponse.userId()
            );

            return tokenResponse;

        } catch (JsonProcessingException exception) {
            log.error(
                    "No se pudo interpretar la respuesta OAuth de Tiendanube. status={}, contentType={}",
                    response.getStatusCode(),
                    response.getHeaders().getContentType()
            );

            throw new TiendanubeApiException(
                    "Tiendanube devolvió una respuesta de autenticación inválida",
                    exception
            );
        }
    }

    private void validateAuthorizationCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("El código de autorización de Tiendanube es obligatorio.");
        }
    }

    private void validateTokenResponse(TiendanubeTokenResponse response) {
        if (response == null) {
            throw new TiendanubeApiException(
                    "Tiendanube devolvió una respuesta de autenticación inválida"
            );
        }

        if (response.accessToken() == null || response.accessToken().isBlank()) {
            throw new TiendanubeApiException(
                    "Tiendanube no devolvió el access token"
            );
        }

        if (response.userId() == null) {
            throw new TiendanubeApiException(
                    "Tiendanube no devolvió el identificador de la tienda"
            );
        }

        if (response.tokenType() == null || response.tokenType().isBlank()) {
            throw new TiendanubeApiException(
                    "Tiendanube no devolvió el tipo de token"
            );
        }
    }

    private String buildAuthorizationHeader(TiendanubeStore store) {
        return BEARER_PREFIX + store.getAccessToken();
    }

    private TiendanubeStore getActiveStore(Long storeId) {
        return storeRepository.findByStoreIdAndActiveTrue(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Tienda Tiendanube no conectada"));
    }

    private TiendanubeApiException buildApiException(String operation, RestClientException exception) {

        if (exception instanceof RestClientResponseException responseException) {
            log.error("Error Tiendanube. operation={}, status={}, response={}",
                    operation, responseException.getStatusCode(), responseException.getResponseBodyAsString());
        } else {
            log.error("Error de comunicación con Tiendanube. operation={}", operation, exception);
        }

        return new TiendanubeApiException("No se pudo completar la operación en Tiendanube: " + operation, exception);
    }
}