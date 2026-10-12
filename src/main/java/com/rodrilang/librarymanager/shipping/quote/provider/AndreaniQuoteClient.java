package com.rodrilang.librarymanager.shipping.quote.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rodrilang.librarymanager.exception.BusinessException;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.time.Duration;
import java.util.Map;

@Component
public class AndreaniQuoteClient {
    private static final String QA_BASE_URL = "https://apisqa.andreani.com";
    private static final String PROD_BASE_URL = "https://apis.andreani.com";
    private final ObjectMapper objectMapper;

    public AndreaniQuoteClient(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public ShippingProviderQuote quoteHome(String postalCode, ShippingPackage pack, Map<String, String> credentials, Map<String, Object> settings) {
        String clientCode = first(credentials, "cliente", "codigoCliente", "clientCode");
        String contract = first(credentials, "contratoDomicilio", "contrato", "contract");
        if (blank(clientCode) || blank(contract)) {
            throw new BusinessException("Andreani requiere cliente y contrato de domicilio para cotizar.");
        }

        String baseUrl = baseUrl(credentials, settings);
        RestClient client = client(baseUrl);
        String token = loginIfConfigured(client, credentials);
        URI uri = UriComponentsBuilder.fromUriString(baseUrl + "/v1/tarifas")
                .queryParam("cpDestino", postalCode)
                .queryParam("contrato", contract)
                .queryParam("cliente", clientCode)
                .queryParam("bultos[0][volumen]", pack.volumeCm3().setScale(0, RoundingMode.CEILING).toPlainString())
                .queryParam("bultos[0][kilos]", BigDecimal.valueOf(pack.weightGrams()).divide(BigDecimal.valueOf(1000), 3, RoundingMode.CEILING).toPlainString())
                .queryParam("bultos[0][altoCm]", pack.heightCm().setScale(1, RoundingMode.CEILING).toPlainString())
                .queryParam("bultos[0][largoCm]", pack.depthCm().setScale(1, RoundingMode.CEILING).toPlainString())
                .queryParam("bultos[0][anchoCm]", pack.widthCm().setScale(1, RoundingMode.CEILING).toPlainString())
                .queryParam("bultos[0][valorDeclarado]", pack.declaredValue().setScale(2, RoundingMode.HALF_UP).toPlainString())
                .build().encode().toUri();
        try {
            RestClient.RequestHeadersSpec<?> request = client.get().uri(uri).accept(MediaType.APPLICATION_JSON);
            if (!blank(token)) {
                request = request.header("Authorization", "Bearer " + token).header("x-authorization-token", token);
            }
            JsonNode body = request.retrieve().body(JsonNode.class);
            if (body == null) throw new BusinessException("Andreani respondió sin datos de cotización.");
            BigDecimal amount = amount(body);
            Integer min = intOrNull(body.path("plazoEntrega").path("minimo"));
            Integer max = intOrNull(body.path("plazoEntrega").path("maximo"));
            String text = deliveryText(min, max);
            return new ShippingProviderQuote(
                    com.rodrilang.librarymanager.shipping.model.ShippingProviderType.ANDREANI,
                    "HOME",
                    "Andreani · Domicilio",
                    com.rodrilang.librarymanager.shipping.model.ShippingDeliveryType.HOME,
                    amount,
                    min,
                    max,
                    text,
                    null,
                    body.toString()
            );
        } catch (RestClientResponseException ex) {
            throw new BusinessException("Andreani no pudo cotizar el envío (HTTP " + ex.getStatusCode().value() + ").");
        } catch (BusinessException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            throw new BusinessException("No se pudo consultar la cotización de Andreani.");
        }
    }

    private RestClient client(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(8));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    private String loginIfConfigured(RestClient client, Map<String, String> credentials) {
        String user = first(credentials, "usuario", "username", "user");
        String password = first(credentials, "password", "contrasena", "pass");
        if (blank(user) || blank(password)) return null;
        try {
            JsonNode response = client.post().uri("/v2/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("usuario", user, "password", password))
                    .retrieve().body(JsonNode.class);
            if (response == null) return null;
            String token = response.path("token").asText(null);
            if (blank(token)) token = response.path("access_token").asText(null);
            return token;
        } catch (RuntimeException ignored) {
            // El cotizador histórico de Andreani puede operar sin autenticación.
            return null;
        }
    }

    private BigDecimal amount(JsonNode body) {
        JsonNode withTax = body.path("tarifaConIva");
        if (withTax.isNumber() || withTax.isTextual()) return decimal(withTax);
        JsonNode total = withTax.path("total");
        if (!total.isMissingNode() && !total.isNull()) return decimal(total);
        throw new BusinessException("Andreani respondió una tarifa sin importe utilizable.");
    }

    private BigDecimal decimal(JsonNode node) {
        try { return new BigDecimal(node.asText()).setScale(2, RoundingMode.HALF_UP); }
        catch (NumberFormatException ex) { throw new BusinessException("Andreani respondió un importe inválido."); }
    }

    private Integer intOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || node.asText().isBlank()) return null;
        return node.asInt();
    }

    private String deliveryText(Integer min, Integer max) {
        if (min == null && max == null) return null;
        if (min != null && max != null && !min.equals(max)) return min + " a " + max + " días hábiles";
        int days = min != null ? min : max;
        return days + (days == 1 ? " día hábil" : " días hábiles");
    }

    private String baseUrl(Map<String, String> credentials, Map<String, Object> settings) {
        String explicit = first(credentials, "baseUrl");
        if (!blank(explicit)) return explicit.replaceAll("/+$", "");
        String credentialEnvironment = first(credentials, "environment", "ambiente");
        Object configuredEnvironment = settings == null ? null : settings.get("environment");
        String environment = !blank(credentialEnvironment) ? credentialEnvironment : (configuredEnvironment == null ? null : String.valueOf(configuredEnvironment));
        return environment != null && ("PRODUCTION".equalsIgnoreCase(environment) || "PROD".equalsIgnoreCase(environment)) ? PROD_BASE_URL : QA_BASE_URL;
    }

    private String first(Map<String, String> values, String... keys) {
        if (values == null) return null;
        for (String key : keys) {
            String value = values.get(key);
            if (!blank(value)) return value.trim();
        }
        return null;
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
