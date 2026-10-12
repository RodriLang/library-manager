package com.rodrilang.librarymanager.shipping.oca;

import com.rodrilang.librarymanager.exception.BusinessException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.*;

@Component
public class OcaClientSupport {
    private static final String QA_BASE = "https://integraciones.ocadev.com.ar/epak_tracking_test/Oep_TrackEPak.asmx";
    private static final String PROD_BASE = "https://webservice.oca.com.ar/ePak_tracking/Oep_TrackEPak.asmx";

    private final RestClient restClient = RestClient.builder().build();

    public String baseUrl(Map<String, Object> settings) {
        String explicit = text(settings.get("baseUrl"));
        if (explicit != null) return stripSlash(explicit);
        String environment = Objects.toString(settings.getOrDefault("environment", "QA"), "QA").trim().toUpperCase(Locale.ROOT);
        return "PRODUCTION".equals(environment) || "PROD".equals(environment) ? PROD_BASE : QA_BASE;
    }

    public String post(Map<String, Object> settings, String operation, Map<String, ?> params) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        params.forEach((key, value) -> form.add(key, value == null ? "" : String.valueOf(value)));
        try {
            return restClient.post().uri(baseUrl(settings) + "/" + operation)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_XML, MediaType.TEXT_XML, MediaType.TEXT_PLAIN)
                    .body(form).retrieve().body(String.class);
        } catch (Exception ex) {
            throw new BusinessException("No pudimos comunicarnos con OCA para " + operation + ".");
        }
    }

    public String require(Map<String, String> credentials, String key, String label) {
        String value = text(credentials.get(key));
        if (value == null) throw new BusinessException("Falta configurar " + label + " de OCA (" + key + ").");
        return value;
    }

    public int requireInt(Map<String, String> credentials, Map<String, Object> settings, String key, String label) {
        String value = text(credentials.get(key));
        if (value == null) value = text(settings.get(key));
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
