package com.rodrilang.librarymanager.integrations.tiendanube.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.config.TiendanubeProperties;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeCreateImageRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeCreateProductRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeCreateWebhookRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeUpdateProductRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeUpdateStockRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.request.TiendanubeUpdateVariantRequest;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeImageResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeOrderResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductVariantResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeProductsPage;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeTokenResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.dto.response.TiendanubeWebhookResponse;
import com.rodrilang.librarymanager.integrations.tiendanube.entity.TiendanubeStore;
import com.rodrilang.librarymanager.integrations.tiendanube.exception.TiendanubeApiException;
import com.rodrilang.librarymanager.integrations.tiendanube.exception.TiendanubeRemoteResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.repository.TiendanubeStoreRepository;
import com.rodrilang.librarymanager.integrations.tiendanube.service.TiendanubeConnectionService;
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
    private final TiendanubeConnectionService connectionService;

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
            throw buildApiException("crear producto", exception, store);
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
            throw buildApiException("obtener pedido", exception, store);
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
            throw buildApiException("obtener producto", exception, store);
        }
    }

    public List<TiendanubeProductResponse> getProducts(Long storeId) {
        TiendanubeStore store = getActiveStore(storeId);

        List<TiendanubeProductResponse> products = new ArrayList<>();
        int page = 1;

        while (true) {
            TiendanubeProductResponse[] currentPage = fetchProductsPage(
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

    public TiendanubeProductsPage fetchProductsPage(
            Long storeId,
            int page,
            int size
    ) {
        TiendanubeStore store = getActiveStore(storeId);

        int remotePage = page + 1;

        try {
            ResponseEntity<TiendanubeProductResponse[]> response =
                    tiendanubeRestClient.get()
                            .uri(
                                    properties.apiUrl()
                                            + "/{storeId}/products?page={page}&per_page={perPage}",
                                    storeId,
                                    remotePage,
                                    size
                            )
                            .header(HttpHeaders.AUTHORIZATION, buildAuthorizationHeader(store))
                            .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                            .accept(MediaType.APPLICATION_JSON)
                            .retrieve()
                            .toEntity(TiendanubeProductResponse[].class);

            TiendanubeProductResponse[] body = response.getBody();

            List<TiendanubeProductResponse> products =
                    body == null
                            ? List.of()
                            : Arrays.asList(body);

            long total = parseTotalCount(response);

            int totalPages =
                    size > 0
                            ? (int) Math.ceil((double) total / size)
                            : 0;

            return new TiendanubeProductsPage(
                    products,
                    total,
                    page,
                    size,
                    totalPages
            );

        } catch (RestClientException exception) {
            throw buildApiException(
                    "obtener lista de productos",
                    exception,
                    store
            );
        }
    }

    public void deleteProduct(
            Long storeId,
            Long productId
    ) {
        TiendanubeStore store = getActiveStore(storeId);

        try {
            tiendanubeRestClient
                    .delete()
                    .uri(
                            properties.apiUrl()
                                    + "/{storeId}/products/{productId}",
                            storeId,
                            productId
                    )
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            buildAuthorizationHeader(store)
                    )
                    .header(
                            HttpHeaders.USER_AGENT,
                            USER_AGENT_VALUE
                    )
                    .retrieve()
                    .toBodilessEntity();

        } catch (RestClientResponseException exception) {
            throw buildApiException(
                    "eliminar publicación",
                    exception
            );
        }
    }

    public void deleteProductImage(
            Long storeId,
            Long productId,
            Long imageId
    ) {
        TiendanubeStore store = getActiveStore(storeId);

        try {
            tiendanubeRestClient
                    .delete()
                    .uri(
                            properties.apiUrl()
                                    + "/{storeId}/products/{productId}/images/{imageId}",
                            storeId,
                            productId,
                            imageId
                    )
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            buildAuthorizationHeader(store)
                    )
                    .header(
                            HttpHeaders.USER_AGENT,
                            USER_AGENT_VALUE
                    )
                    .retrieve()
                    .toBodilessEntity();

        } catch (RestClientException exception) {
            throw buildApiException("eliminar imagen de producto", exception, store);
        }
    }

    private long parseTotalCount(ResponseEntity<?> response) {
        String value = response.getHeaders().getFirst("x-total-count");

        if (value == null || value.isBlank()) {
            return 0;
        }

        try {
            return Long.parseLong(value);
        } catch (NumberFormatException exception) {
            log.warn("No se pudo interpretar x-total-count de Tiendanube: {}", value);

            return 0;
        }
    }

    public TiendanubeImageResponse createProductImage(
            Long storeId,
            Long productId,
            TiendanubeCreateImageRequest request
    ) {
        TiendanubeStore store = getActiveStore(storeId);

        try {
            return tiendanubeRestClient.post()
                    .uri(properties.apiUrl() + "/{storeId}/products/{productId}/images", storeId, productId)
                    .header(HttpHeaders.AUTHORIZATION, buildAuthorizationHeader(store))
                    .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(TiendanubeImageResponse.class);

        } catch (RestClientException exception) {
            throw buildApiException("crear imagen de producto", exception, store);
        }
    }

    private TiendanubeProductResponse[] fetchProductsPage(
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
            throw buildApiException("obtener producto", exception, store);
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
            throw buildApiException("actualizar stock", exception, store);
        }
    }

    public TiendanubeProductVariantResponse updateVariant(
            Long storeId,
            Long productId,
            Long variantId,
            TiendanubeUpdateVariantRequest request
    ) {
        TiendanubeStore store = getActiveStore(storeId);

        try {
            return tiendanubeRestClient.put()
                    .uri(
                            properties.apiUrl()
                                    + "/{storeId}/products/{productId}/variants/{variantId}",
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
            throw buildApiException("actualizar variante", exception, store);
        }
    }

    public TiendanubeProductResponse updateProduct(
            Long storeId,
            Long productId,
            TiendanubeUpdateProductRequest request
    ) {
        TiendanubeStore store = getActiveStore(storeId);

        try {
            return tiendanubeRestClient.put()
                    .uri(
                            properties.apiUrl()
                                    + "/{storeId}/products/{productId}",
                            storeId,
                            productId
                    )
                    .header(
                            HttpHeaders.AUTHORIZATION,
                            buildAuthorizationHeader(store)
                    )
                    .header(
                            HttpHeaders.USER_AGENT,
                            USER_AGENT_VALUE
                    )
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(TiendanubeProductResponse.class);

        } catch (RestClientException exception) {
            throw buildApiException(
                    "actualizar publicación",
                    exception,
                    store
            );
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
            throw buildApiException("crear webhook", exception, store);
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
        TiendanubeStore store = storeRepository.findByStoreIdAndActiveTrue(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Tienda Tiendanube no conectada"));

        if (!store.isTokenValid()) {
            throw new TiendanubeApiException("La conexión con Tiendanube necesita volver a autorizarse.");
        }

        return store;
    }

    private TiendanubeApiException buildApiException(
            String operation,
            RestClientException exception
    ) {
        if (exception instanceof RestClientResponseException responseException) {
            log.error(
                    "Error Tiendanube. operation={}, status={}, response={}",
                    operation,
                    responseException.getStatusCode(),
                    responseException.getResponseBodyAsString()
            );
        } else {
            log.error(
                    "Error de comunicación con Tiendanube. operation={}",
                    operation,
                    exception
            );
        }

        return new TiendanubeApiException(
                "No se pudo completar la operación en Tiendanube: " + operation,
                exception
        );
    }

    private TiendanubeApiException buildApiException(
            String operation,
            RestClientException exception,
            TiendanubeStore store
    ) {
        if (exception instanceof RestClientResponseException responseException) {
            int status = responseException.getStatusCode().value();

            log.error(
                    "Error Tiendanube. operation={}, storeId={}, status={}, response={}",
                    operation,
                    store.getStoreId(),
                    status,
                    responseException.getResponseBodyAsString()
            );

            if (status == 401) {
                connectionService.markTokenInvalid(
                        store.getStoreId(),
                        "INVALID_ACCESS_TOKEN"
                );

                return new TiendanubeApiException(
                        "La conexión con Tiendanube dejó de ser válida. Es necesario volver a conectar la cuenta.",
                        responseException
                );
            }

            if (status == 404) {
                return new TiendanubeRemoteResourceNotFoundException(
                        "El recurso ya no existe en Tiendanube",
                        responseException
                );
            }
        } else {
            log.error(
                    "Error de comunicación con Tiendanube. operation={}, storeId={}",
                    operation,
                    store.getStoreId(),
                    exception
            );
        }

        return new TiendanubeApiException(
                "No se pudo completar la operación en Tiendanube: " + operation,
                exception
        );
    }
}