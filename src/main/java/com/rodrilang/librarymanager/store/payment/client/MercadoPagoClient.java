package com.rodrilang.librarymanager.store.payment.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.rodrilang.librarymanager.exception.BusinessException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.Map;

@Component
public class MercadoPagoClient {
    private final RestClient client;

    public MercadoPagoClient(RestClient.Builder builder) {
        this.client = builder.baseUrl("https://api.mercadopago.com").build();
    }

    public JsonNode exchangeAuthorizationCode(String clientId, String clientSecret, String code,
                                              String redirectUri, String codeVerifier) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", redirectUri);
        form.add("code_verifier", codeVerifier);
        return oauthToken(form, "No pudimos completar la autorización con Mercado Pago.");
    }

    public JsonNode refreshToken(String clientId, String clientSecret, String refreshToken) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", clientId);
        form.add("client_secret", clientSecret);
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return oauthToken(form, "No pudimos renovar la conexión con Mercado Pago.");
    }

    private JsonNode oauthToken(MultiValueMap<String, String> form, String message) {
        try {
            return client.post()
                    .uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new BusinessException(message + " " + readable(e));
        } catch (Exception e) {
            throw new BusinessException(message);
        }
    }

    public JsonNode createOrder(String accessToken, String idempotencyKey, Map<String, Object> body) {
        try {
            return client.post()
                    .uri("/v1/orders")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("X-Idempotency-Key", idempotencyKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new BusinessException("Mercado Pago rechazó la creación del pago: " + readable(e));
        } catch (Exception e) {
            throw new BusinessException("No pudimos conectarnos con Mercado Pago. Intentá nuevamente.");
        }
    }

    public JsonNode getOrder(String accessToken, String externalOrderId) {
        try {
            return client.get()
                    .uri("/v1/orders/{id}", externalOrderId)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new BusinessException("No pudimos consultar el pago en Mercado Pago: " + readable(e));
        } catch (Exception e) {
            throw new BusinessException("No pudimos conectarnos con Mercado Pago.");
        }
    }

    private String readable(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (body == null || body.isBlank()) return "HTTP " + e.getStatusCode().value();
        return body.length() > 350 ? body.substring(0, 350) : body;
    }
}
