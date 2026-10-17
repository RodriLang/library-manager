package com.rodrilang.librarymanager.store.payment.client;

import com.fasterxml.jackson.databind.JsonNode;
import com.rodrilang.librarymanager.exception.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class MercadoPagoAccountClient {
    private final RestClient client;

    public MercadoPagoAccountClient(RestClient.Builder builder) {
        this.client = builder.baseUrl("https://api.mercadolibre.com").build();
    }

    public JsonNode getCurrentUser(String accessToken) {
        try {
            return client.get()
                    .uri("/users/me")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new BusinessException("No pudimos obtener los datos de la cuenta de Mercado Pago. " + readable(e));
        } catch (Exception e) {
            throw new BusinessException("No pudimos consultar los datos de la cuenta de Mercado Pago.");
        }
    }

    private String readable(RestClientResponseException e) {
        String body = e.getResponseBodyAsString();
        if (body == null || body.isBlank()) return "HTTP " + e.getStatusCode().value();
        return body.length() > 350 ? body.substring(0, 350) : body;
    }
}
