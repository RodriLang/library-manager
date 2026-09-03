package com.rodrilang.librarymanager.integrations.tiendanube.client;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
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
import com.rodrilang.librarymanager.integrations.tiendanube.exception.TiendanubeApiErrorKind;
import com.rodrilang.librarymanager.integrations.tiendanube.exception.TiendanubeApiException;
import com.rodrilang.librarymanager.integrations.tiendanube.exception.TiendanubeRemoteResourceNotFoundException;
import com.rodrilang.librarymanager.integrations.tiendanube.ratelimit.service.TiendanubeApiRateLimitService;
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

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class TiendanubeClient {

    private static final String USER_AGENT_VALUE = "Library Manager (Rodrigolang90@gmail.com)";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int PRODUCTS_PAGE_SIZE = 200;
    private static final int PRODUCT_SEARCH_SIZE = 200;
    private static final int MAX_REMOTE_ERROR_LENGTH = 1200;

    private final TiendanubeStoreRepository storeRepository;
    private final TiendanubeProperties properties;
    private final RestClient tiendanubeRestClient;
    private final ObjectMapper objectMapper;
    private final TiendanubeConnectionService connectionService;
    private final TiendanubeApiRateLimitService rateLimitService;

    public TiendanubeProductResponse createProduct(Long storeId, TiendanubeCreateProductRequest request) {
        TiendanubeStore store = getActiveStore(storeId);
        return executeBody(store, "crear producto", () -> tiendanubeRestClient.post()
                .uri(properties.endpoints().products(), storeId)
                .header(HttpHeaders.AUTHORIZATION, buildAuthorizationHeader(store))
                .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(TiendanubeProductResponse.class));
    }

    public TiendanubeOrderResponse getOrder(Long storeId, Long orderId) {
        TiendanubeStore store = getActiveStore(storeId);
        return executeBody(store, "obtener pedido", () -> tiendanubeRestClient.get()
                .uri(properties.endpoints().orders(), storeId, orderId)
                .header(HttpHeaders.AUTHORIZATION, buildAuthorizationHeader(store))
                .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(TiendanubeOrderResponse.class));
    }

    public TiendanubeProductResponse getProduct(Long storeId, Long productId) {
        TiendanubeStore store = getActiveStore(storeId);
        return executeBody(store, "obtener producto", () -> tiendanubeRestClient.get()
                .uri(properties.endpoints().product(), storeId, productId)
                .header(HttpHeaders.AUTHORIZATION, buildAuthorizationHeader(store))
                .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .toEntity(TiendanubeProductResponse.class));
    }

    public Optional<TiendanubeProductResponse> findProductBySku(Long storeId, String sku) {
        if (sku == null || sku.isBlank()) {
            return Optional.empty();
        }

        TiendanubeStore store = getActiveStore(storeId);
        String endpoint = properties.endpoints().products() + "/sku/{sku}";

        try {
            return Optional.ofNullable(executeBody(store, "buscar producto por SKU", () -> tiendanubeRestClient.get()
                    .uri(endpoint, storeId, sku)
                    .header(HttpHeaders.AUTHORIZATION, buildAuthorizationHeader(store))
                    .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toEntity(TiendanubeProductResponse.class)));
        } catch (TiendanubeRemoteResourceNotFoundException exception) {
            return Optional.empty();
        }
    }

    public List<TiendanubeProductResponse> searchProducts(Long storeId, String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        TiendanubeStore store = getActiveStore(storeId);
        List<TiendanubeProductResponse> products = new ArrayList<>();
        int page = 1;

        while (true) {
            int currentPage = page;
            ResponseEntity<TiendanubeProductResponse[]> response = executeEntity(
                    store,
                    "buscar productos",
                    () -> tiendanubeRestClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path(properties.endpoints().products())
                                    .queryParam("q", query)
                                    .queryParam("page", currentPage)
                                    .queryParam("per_page", PRODUCT_SEARCH_SIZE)
                                    .build(storeId))
                            .header(HttpHeaders.AUTHORIZATION, buildAuthorizationHeader(store))
                            .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                            .accept(MediaType.APPLICATION_JSON)
                            .retrieve()
                            .toEntity(TiendanubeProductResponse[].class)
            );

            TiendanubeProductResponse[] body = response.getBody();

            if (body == null || body.length == 0) {
                break;
            }

            products.addAll(Arrays.asList(body));

            if (body.length < PRODUCT_SEARCH_SIZE) {
                break;
            }

            page++;
        }

        return products;
    }

    public List<TiendanubeProductResponse> getProducts(Long storeId) {
        TiendanubeStore store = getActiveStore(storeId);
        List<TiendanubeProductResponse> products = new ArrayList<>();
        int page = 1;

        while (true) {
            TiendanubeProductResponse[] currentPage = fetchProductsPage(store, storeId, page, PRODUCTS_PAGE_SIZE);

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

    public TiendanubeProductsPage fetchProductsPage(Long storeId, int page, int size) {
        TiendanubeStore store = getActiveStore(storeId);
        int remotePage = page + 1;

        ResponseEntity<TiendanubeProductResponse[]> response = executeEntity(
                store,
                "obtener lista de productos",
                () -> tiendanubeRestClient.get()
                        .uri(properties.endpoints().productsPage(), storeId, remotePage, size)
                        .header(HttpHeaders.AUTHORIZATION, buildAuthorizationHeader(store))
                        .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .toEntity(TiendanubeProductResponse[].class)
        );

        TiendanubeProductResponse[] body = response.getBody();
        List<TiendanubeProductResponse> products = body == null ? List.of() : Arrays.asList(body);
        long total = parseTotalCount(response);
        int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;

        return new TiendanubeProductsPage(products, total, page, size, totalPages);
    }

    public void deleteProduct(Long storeId, Long productId) {
        TiendanubeStore store = getActiveStore(storeId);
        executeEntity(store, "eliminar publicación", () -> tiendanubeRestClient.delete()
                .uri(properties.endpoints().product(), storeId, productId)
                .header(HttpHeaders.AUTHORIZATION, buildAuthorizationHeader(store))
                .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                .retrieve()
                .toBodilessEntity());
    }

    public void deleteProductImage(Long storeId, Long productId, Long imageId) {
        TiendanubeStore store = getActiveStore(storeId);
        executeEntity(store, "eliminar imagen de producto", () -> tiendanubeRestClient.delete()
                .uri(properties.endpoints().productImage(), storeId, productId, imageId)
                .header(HttpHeaders.AUTHORIZATION, buildAuthorizationHeader(store))
                .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                .retrieve()
                .toBodilessEntity());
    }

    public TiendanubeImageResponse createProductImage(Long storeId, Long productId, TiendanubeCreateImageRequest request) {
        TiendanubeStore store = getActiveStore(storeId);
        return executeBody(store, "crear imagen de producto", () -> tiendanubeRestClient.post()
                .uri(properties.endpoints().productImages(), storeId, productId)
                .header(HttpHeaders.AUTHORIZATION, buildAuthorizationHeader(store))
                .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(TiendanubeImageResponse.class));
    }

    public void updateStock(Long storeId, Long productId, Long variantId, Integer stock) {
        TiendanubeStore store = getActiveStore(storeId);
        TiendanubeUpdateStockRequest request = new TiendanubeUpdateStockRequest(true, stock);

        executeEntity(store, "actualizar stock", () -> tiendanubeRestClient.put()
                .uri(properties.endpoints().productVariant(), storeId, productId, variantId)
                .header(HttpHeaders.AUTHORIZATION, buildAuthorizationHeader(store))
                .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(TiendanubeProductVariantResponse.class));
    }

    public void updateVariant(Long storeId, Long productId, Long variantId, TiendanubeUpdateVariantRequest request) {
        TiendanubeStore store = getActiveStore(storeId);
        executeEntity(store, "actualizar variante", () -> tiendanubeRestClient.put()
                .uri(properties.endpoints().productVariant(), storeId, productId, variantId)
                .header(HttpHeaders.AUTHORIZATION, buildAuthorizationHeader(store))
                .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(TiendanubeProductVariantResponse.class));
    }

    public void updateProduct(Long storeId, Long productId, TiendanubeUpdateProductRequest request) {
        TiendanubeStore store = getActiveStore(storeId);
        executeEntity(store, "actualizar publicación", () -> tiendanubeRestClient.put()
                .uri(properties.endpoints().product(), storeId, productId)
                .header(HttpHeaders.AUTHORIZATION, buildAuthorizationHeader(store))
                .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(TiendanubeProductResponse.class));
    }

    public TiendanubeWebhookResponse createWebhook(Long storeId, String event, String url) {
        TiendanubeStore store = getActiveStore(storeId);
        TiendanubeCreateWebhookRequest request = new TiendanubeCreateWebhookRequest(event, url);
        return executeBody(store, "crear webhook", () -> tiendanubeRestClient.post()
                .uri(properties.endpoints().webhooks(), storeId)
                .header(HttpHeaders.AUTHORIZATION, buildAuthorizationHeader(store))
                .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toEntity(TiendanubeWebhookResponse.class));
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
            throw buildApiException("autenticación con Tiendanube", exception, null);
        }
    }

    private TiendanubeProductResponse[] fetchProductsPage(TiendanubeStore store, Long storeId, int page, int perPage) {
        ResponseEntity<TiendanubeProductResponse[]> response = executeEntity(
                store,
                "obtener productos",
                () -> tiendanubeRestClient.get()
                        .uri(properties.endpoints().productsPage(), storeId, page, perPage)
                        .header(HttpHeaders.AUTHORIZATION, buildAuthorizationHeader(store))
                        .header(HttpHeaders.USER_AGENT, USER_AGENT_VALUE)
                        .accept(MediaType.APPLICATION_JSON)
                        .retrieve()
                        .toEntity(TiendanubeProductResponse[].class)
        );

        return response.getBody();
    }

    private <T> T executeBody(TiendanubeStore store, String operation, Supplier<ResponseEntity<T>> request) {
        return executeEntity(store, operation, request).getBody();
    }

    private <T> ResponseEntity<T> executeEntity(TiendanubeStore store, String operation,
                                                Supplier<ResponseEntity<T>> request) {
        rateLimitService.beforeRequest(store);

        try {
            ResponseEntity<T> response = request.get();
            rateLimitService.registerResponse(store, response.getHeaders());
            return response;
        } catch (RestClientException exception) {
            throw buildApiException(operation, exception, store);
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

    private TiendanubeTokenResponse parseTokenResponse(ResponseEntity<String> response) {
        String body = response.getBody();

        if (body == null || body.isBlank()) {
            throw new TiendanubeApiException("Tiendanube devolvió una respuesta de autenticación vacía");
        }

        try {
            TiendanubeTokenResponse tokenResponse = objectMapper.readValue(body, TiendanubeTokenResponse.class);
            validateTokenResponse(tokenResponse);

            log.info("Autenticación con Tiendanube completada para storeId={}", tokenResponse.userId());
            return tokenResponse;
        } catch (JsonProcessingException exception) {
            log.error("No se pudo interpretar la respuesta OAuth de Tiendanube. status={}, contentType={}",
                    response.getStatusCode(), response.getHeaders().getContentType());
            throw new TiendanubeApiException("Tiendanube devolvió una respuesta de autenticación inválida", exception);
        }
    }

    private void validateAuthorizationCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("El código de autorización de Tiendanube es obligatorio.");
        }
    }

    private void validateTokenResponse(TiendanubeTokenResponse response) {
        if (response == null) {
            throw new TiendanubeApiException("Tiendanube devolvió una respuesta de autenticación inválida");
        }

        if (response.accessToken() == null || response.accessToken().isBlank()) {
            throw new TiendanubeApiException("Tiendanube no devolvió el access token");
        }

        if (response.userId() == null) {
            throw new TiendanubeApiException("Tiendanube no devolvió el identificador de la tienda");
        }

        if (response.tokenType() == null || response.tokenType().isBlank()) {
            throw new TiendanubeApiException("Tiendanube no devolvió el tipo de token");
        }
    }

    private String buildAuthorizationHeader(TiendanubeStore store) {
        return BEARER_PREFIX + store.getAccessToken();
    }

    private TiendanubeStore getActiveStore(Long storeId) {
        TiendanubeStore store = storeRepository.findByStoreIdAndActiveTrue(storeId)
                .orElseThrow(() -> new ResourceNotFoundException("Tienda Tiendanube no conectada"));

        if (!store.isTokenValid()) {
            throw new TiendanubeApiException(
                    "La conexión con Tiendanube necesita volver a autorizarse.",
                    null,
                    "validar conexión",
                    401,
                    "INVALID_ACCESS_TOKEN",
                    TiendanubeApiErrorKind.AUTHENTICATION,
                    null
            );
        }

        return store;
    }

    private TiendanubeApiException buildApiException(String operation, RestClientException exception,
                                                      TiendanubeStore store) {
        if (!(exception instanceof RestClientResponseException responseException)) {
            TiendanubeApiErrorKind kind = isTimeout(exception)
                    ? TiendanubeApiErrorKind.TIMEOUT
                    : TiendanubeApiErrorKind.NETWORK;

            log.error("Error de comunicación con Tiendanube. operation={}, storeId={}, kind={}",
                    operation, store == null ? null : store.getStoreId(), kind, exception);

            return new TiendanubeApiException(
                    "No se pudo comunicar con Tiendanube al " + operation,
                    exception,
                    operation,
                    null,
                    kind.name(),
                    kind,
                    null
            );
        }

        int status = responseException.getStatusCode().value();
        HttpHeaders headers = responseException.getResponseHeaders();
        TiendanubeApiErrorKind kind = classify(status);
        String remoteCode = resolveRemoteErrorCode(responseException, status);
        String remoteMessage = resolveRemoteMessage(responseException);
        Duration retryAfter = null;

        if (store != null) {
            rateLimitService.registerResponse(store, headers);

            if (status == 429) {
                retryAfter = rateLimitService.registerRateLimited(store, headers);
            } else if (status >= 500) {
                retryAfter = rateLimitService.resolveRetryAfter(headers);
            }
        }

        if (status == 401 && store != null) {
            try {
                connectionService.markTokenInvalid(store.getStoreId(), "INVALID_ACCESS_TOKEN");
            } catch (RuntimeException persistenceException) {
                log.error("No se pudo persistir la invalidación del token Tiendanube. storeId={}",
                        store.getStoreId(), persistenceException);
            }
        }

        if (status == 404) {
            log.debug("Recurso Tiendanube no encontrado. operation={} storeId={} code={}",
                    operation, store == null ? null : store.getStoreId(), remoteCode);
        } else {
            log.error("Error Tiendanube. operation={}, storeId={}, status={}, kind={}, code={}, response={}",
                    operation,
                    store == null ? null : store.getStoreId(),
                    status,
                    kind,
                    remoteCode,
                    truncate(responseException.getResponseBodyAsString())
            );
        }

        String message = buildErrorMessage(operation, status, remoteMessage);

        if (status == 404) {
            return new TiendanubeRemoteResourceNotFoundException(operation, message, remoteCode, responseException);
        }

        return new TiendanubeApiException(
                message,
                responseException,
                operation,
                status,
                remoteCode,
                kind,
                retryAfter
        );
    }

    private TiendanubeApiErrorKind classify(int status) {
        if (status == 401) {
            return TiendanubeApiErrorKind.AUTHENTICATION;
        }

        if (status == 402) {
            return TiendanubeApiErrorKind.ACCESS_SUSPENDED;
        }

        if (status == 403) {
            return TiendanubeApiErrorKind.AUTHORIZATION;
        }

        if (status == 404) {
            return TiendanubeApiErrorKind.NOT_FOUND;
        }

        if (status == 408) {
            return TiendanubeApiErrorKind.TIMEOUT;
        }

        if (status == 429) {
            return TiendanubeApiErrorKind.RATE_LIMIT;
        }

        if (status == 425) {
            return TiendanubeApiErrorKind.SERVER_ERROR;
        }

        if (status >= 500) {
            return TiendanubeApiErrorKind.SERVER_ERROR;
        }

        if (status >= 400) {
            return TiendanubeApiErrorKind.CLIENT_ERROR;
        }

        return TiendanubeApiErrorKind.UNKNOWN;
    }

    private boolean isTimeout(Throwable throwable) {
        Throwable current = throwable;

        while (current != null) {
            if (current instanceof SocketTimeoutException || current instanceof HttpTimeoutException) {
                return true;
            }

            current = current.getCause();
        }

        return false;
    }

    private String resolveRemoteErrorCode(RestClientResponseException exception, int status) {
        JsonNode body = parseErrorBody(exception);

        if (body != null) {
            JsonNode code = body.get("code");

            if (code != null && !code.isNull() && !code.asText().isBlank()) {
                return code.asText();
            }
        }

        return "HTTP_" + status;
    }

    private String resolveRemoteMessage(RestClientResponseException exception) {
        JsonNode body = parseErrorBody(exception);

        if (body != null) {
            for (String field : List.of("description", "message", "error")) {
                JsonNode value = body.get(field);

                if (value != null && value.isTextual() && !value.asText().isBlank()) {
                    return truncate(value.asText());
                }
            }
        }

        String raw = exception.getResponseBodyAsString();
        return raw == null || raw.isBlank() ? null : truncate(raw);
    }

    private JsonNode parseErrorBody(RestClientResponseException exception) {
        String body = exception.getResponseBodyAsString();

        if (body == null || body.isBlank()) {
            return null;
        }

        try {
            return objectMapper.readTree(body);
        } catch (JsonProcessingException ignored) {
            return null;
        }
    }

    private String buildErrorMessage(String operation, int status, String remoteMessage) {
        String base = "Tiendanube rechazó la operación '" + operation + "' (HTTP " + status + ")";
        return remoteMessage == null || remoteMessage.isBlank() ? base : base + ": " + remoteMessage;
    }

    private String truncate(String value) {
        if (value == null || value.length() <= MAX_REMOTE_ERROR_LENGTH) {
            return value;
        }

        return value.substring(0, MAX_REMOTE_ERROR_LENGTH) + "…";
    }
}
