package com.rodrilang.librarymanager.shipping.correoargentino;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rodrilang.librarymanager.exception.BusinessException;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CorreoArgentinoClientSupport {
    public static final String MICORREO_TEST = "https://apitest.correoargentino.com.ar/micorreo/v1";
    public static final String MICORREO_PROD = "https://api.correoargentino.com.ar/micorreo/v1";
    public static final String PAQAR_TEST = "https://apitest.correoargentino.com.ar/paqar/v1";
    public static final String PAQAR_PROD = "https://api.correoargentino.com.ar/paqar/v1";

    private final ObjectMapper objectMapper;
    private final Map<String, CachedToken> tokens = new ConcurrentHashMap<>();

    public CorreoArgentinoClientSupport(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public RestClient client(String baseUrl) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(12));
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }

    public String miCorreoBaseUrl(Map<String, Object> settings) {
        String explicit = text(settings.get("miCorreoBaseUrl"));
        if (explicit != null) return stripSlash(explicit);
        return production(settings) ? MICORREO_PROD : MICORREO_TEST;
    }

    public String paqArBaseUrl(Map<String, Object> settings) {
        String explicit = text(settings.get("paqArBaseUrl"));
        if (explicit != null) return stripSlash(explicit);
        return production(settings) ? PAQAR_PROD : PAQAR_TEST;
    }

    public String miCorreoToken(Map<String, String> credentials, Map<String, Object> settings) {
        String user = first(credentials, "usuario", "username", "user", "miCorreoUsuario");
        String password = first(credentials, "password", "contrasena", "miCorreoPassword");
        if (blank(user) || blank(password)) {
            throw new BusinessException("Correo Argentino MiCorreo requiere usuario y contraseña API.");
        }
        String baseUrl = miCorreoBaseUrl(settings);
        String key = baseUrl + "|" + user;
        CachedToken cached = tokens.get(key);
        if (cached != null && cached.expiresAt().isAfter(Instant.now().plusSeconds(60))) return cached.token();

        try {
            String basic = Base64.getEncoder().encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
            JsonNode body = client(baseUrl).post().uri("/token")
                    .header("Authorization", "Basic " + basic)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve().body(JsonNode.class);
            String token = body == null ? null : body.path("token").asText(null);
            if (blank(token)) throw new BusinessException("Correo Argentino no devolvió un token de MiCorreo.");
            Instant expiresAt = parseExpiry(body == null ? null : body.path("expires").asText(null));
            tokens.put(key, new CachedToken(token, expiresAt));
            return token;
        } catch (RestClientResponseException ex) {
            throw new BusinessException("Correo Argentino rechazó las credenciales de MiCorreo (HTTP " + ex.getStatusCode().value() + ").");
        }
    }

    public void addPaqArHeaders(org.springframework.http.HttpHeaders headers, Map<String, String> credentials) {
        String agreement = first(credentials, "agreement", "acuerdo", "agreementId");
        String apiKey = first(credentials, "apiKey", "apikey", "key", "paqArApiKey");
        if (blank(agreement) || blank(apiKey)) {
            throw new BusinessException("Correo Argentino PAQ.AR requiere agreement y API-Key.");
        }
        headers.set("Authorization", "Apikey " + apiKey);
        headers.set("agreement", agreement);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
    }

    public String customerId(Map<String, String> credentials) {
        String id = first(credentials, "customerId", "cliente", "idCliente", "miCorreoCustomerId");
        if (blank(id)) throw new BusinessException("Correo Argentino MiCorreo requiere customerId.");
        return id;
    }

    public String agreement(Map<String, String> credentials) {
        return first(credentials, "agreement", "acuerdo", "agreementId");
    }

    public String sellerId(Map<String, String> credentials) {
        return first(credentials, "sellerId", "seller");
    }

    public String serviceType(Map<String, String> credentials, Map<String, Object> settings) {
        String configured = first(credentials, "serviceType", "tipoServicio");
        if (blank(configured)) configured = text(settings.get("serviceType"));
        return blank(configured) ? "CP" : configured.trim();
    }

    public boolean hasMiCorreoCredentials(Map<String, String> credentials) {
        return !blank(first(credentials, "usuario", "username", "user", "miCorreoUsuario"))
                && !blank(first(credentials, "password", "contrasena", "miCorreoPassword"))
                && !blank(first(credentials, "customerId", "cliente", "idCliente", "miCorreoCustomerId"));
    }

    public boolean hasPaqArCredentials(Map<String, String> credentials) {
        return !blank(first(credentials, "agreement", "acuerdo", "agreementId"))
                && !blank(first(credentials, "apiKey", "apikey", "key", "paqArApiKey"));
    }

    public String first(Map<String, String> values, String... keys) {
        for (String key : keys) {
            String value = values.get(key);
            if (!blank(value)) return value.trim();
        }
        return null;
    }

    public String text(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private boolean production(Map<String, Object> settings) {
        String env = text(settings.get("environment"));
        return env != null && (env.equalsIgnoreCase("PRODUCTION") || env.equalsIgnoreCase("PROD"));
    }

    private Instant parseExpiry(String value) {
        if (!blank(value)) {
            try {
                java.time.LocalDateTime local = java.time.LocalDateTime.parse(value, java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
                return local.atZone(java.time.ZoneId.of("America/Argentina/Buenos_Aires")).toInstant().minusSeconds(60);
            } catch (RuntimeException ignored) { }
        }
        return Instant.now().plus(Duration.ofMinutes(50));
    }

    private String stripSlash(String value) { return value.endsWith("/") ? value.substring(0, value.length() - 1) : value; }
    private boolean blank(String value) { return value == null || value.isBlank(); }
    private record CachedToken(String token, Instant expiresAt) { }
}
