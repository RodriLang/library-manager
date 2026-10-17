package com.rodrilang.librarymanager.shipping.oca;

import com.rodrilang.librarymanager.exception.BusinessException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.*;
import java.util.regex.Pattern;

@Component
public class OcaClientSupport {
    private static final String QA_BASE = "https://integraciones.ocadev.com.ar/epak_tracking_test/Oep_TrackEPak.asmx";
    private static final String PROD_BASE = "https://webservice.oca.com.ar/ePak_tracking/Oep_TrackEPak.asmx";

    private static final int RESPONSE_PREVIEW_LIMIT = 1200;
    private static final Pattern PASSWORD_XML = Pattern.compile("(?is)(<\\s*(?:psw|password)\\s*>).*?(<\\s*/\\s*(?:psw|password)\\s*>)");
    private final RestClient restClient = RestClient.builder().build();

    public String environment(Map<String, Object> settings) {
        String environment = Objects.toString(settings.getOrDefault("environment", "QA"), "QA").trim().toUpperCase(Locale.ROOT);
        return "PRODUCTION".equals(environment) || "PROD".equals(environment) ? "PRODUCTION" : "QA";
    }

    public String baseUrl(Map<String, Object> settings) {
        String explicit = text(settings.get("baseUrl"));
        if (explicit != null) return stripSlash(explicit);
        return "PRODUCTION".equals(environment(settings)) ? PROD_BASE : QA_BASE;
    }

    public String post(Map<String, Object> settings, String operation, Map<String, ?> params) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        params.forEach((key, value) -> form.add(key, value == null ? "" : String.valueOf(value)));
        try {
            return restClient.post().uri(baseUrl(settings) + "/" + operation)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_XML, MediaType.TEXT_XML, MediaType.TEXT_PLAIN)
                    .body(form).retrieve().body(String.class);
        } catch (RestClientResponseException ex) {
            String detail = responsePreview(ex.getResponseBodyAsString());
            throw new BusinessException("OCA respondió HTTP " + ex.getStatusCode().value() + " al ejecutar " + operation
                    + (detail.isBlank() ? "." : ": " + detail));
        } catch (Exception ex) {
            String detail = ex.getMessage() == null ? "" : ex.getMessage().trim();
            throw new BusinessException("No pudimos comunicarnos con OCA para " + operation
                    + (detail.isBlank() ? "." : ": " + detail));
        }
    }

    public String responsePreview(String body) {
        if (body == null || body.isBlank()) return "";
        String compact = body.replaceAll("[\\r\\n\\t]+", " ").replaceAll("\\s{2,}", " ").trim();
        compact = PASSWORD_XML.matcher(compact).replaceAll("$1***$2");
        return compact.length() <= RESPONSE_PREVIEW_LIMIT ? compact : compact.substring(0, RESPONSE_PREVIEW_LIMIT) + "…";
    }

    public String require(Map<String, String> credentials, String key, String label) {
        String value = text(credentials.get(key));
        if (value == null) throw new BusinessException("Falta configurar " + label + " de OCA (" + key + ").");
        return value;
    }

    public String requireSetting(Map<String, Object> settings, Map<String, String> legacyCredentials, String key, String label) {
        String value = text(settings.get(key));
        if (value == null) value = text(legacyCredentials.get(key));
        if (value == null) throw new BusinessException("Falta configurar " + label + " de OCA (" + key + ").");
        return value;
    }

    public int requireInt(Map<String, String> legacyCredentials, Map<String, Object> settings, String key, String label) {
        String value = text(settings.get(key));
        if (value == null) value = text(legacyCredentials.get(key));
        if (value == null) throw new BusinessException("Falta configurar " + label + " de OCA (" + key + ").");
        try { return Integer.parseInt(value); }
        catch (NumberFormatException ex) { throw new BusinessException(label + " de OCA no es válido."); }
    }

    public String setting(Map<String, Object> settings, String key, String fallback) {
        String value = text(settings.get(key));
        return value == null ? fallback : value;
    }

    public String trackingUrl(String trackingNumber) {
        if (trackingNumber == null || trackingNumber.isBlank()) return null;
        return "https://www.oca.com.ar/Busquedas/Envios/" + trackingNumber.trim();
    }

    private String text(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
    private String stripSlash(String value) { return value.endsWith("/") ? value.substring(0, value.length()-1) : value; }
}
