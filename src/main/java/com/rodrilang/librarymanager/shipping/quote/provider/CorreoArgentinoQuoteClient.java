package com.rodrilang.librarymanager.shipping.quote.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.shipping.correoargentino.CorreoArgentinoClientSupport;
import com.rodrilang.librarymanager.shipping.model.ShippingDeliveryType;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Component
@RequiredArgsConstructor
public class CorreoArgentinoQuoteClient {
    private final CorreoArgentinoClientSupport support;

    public List<ShippingProviderQuote> quote(String originPostalCode, String destinationPostalCode, ShippingPackage pack,
                                             Map<String, String> credentials, Map<String, Object> settings) {
        String token = support.miCorreoToken(credentials, settings);
        String customerId = support.customerId(credentials);
        Map<String, Object> dimensions = new LinkedHashMap<>();
        dimensions.put("weight", Math.max(1, pack.weightGrams()));
        dimensions.put("height", cm(pack.heightCm()));
        dimensions.put("width", cm(pack.widthCm()));
        dimensions.put("length", cm(pack.depthCm()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("customerId", customerId);
        body.put("postalCodeOrigin", normalizePostal(originPostalCode));
        body.put("postalCodeDestination", normalizePostal(destinationPostalCode));
        body.put("dimensions", dimensions);

        try {
            JsonNode response = support.client(support.miCorreoBaseUrl(settings)).post().uri("/rates")
                    .header("Authorization", "Bearer " + token)
                    .contentType(MediaType.APPLICATION_JSON).accept(MediaType.APPLICATION_JSON)
                    .body(body).retrieve().body(JsonNode.class);
            if (response == null || !response.path("rates").isArray()) throw new BusinessException("Correo Argentino respondió sin tarifas.");
            List<ShippingProviderQuote> quotes = new ArrayList<>();
            for (JsonNode rate : response.path("rates")) {
                String deliveredType = rate.path("deliveredType").asText("");
                String productType = rate.path("productType").asText("CP");
                String productName = rate.path("productName").asText("PAQ.AR");
                BigDecimal price = rate.path("price").decimalValue();
                ShippingDeliveryType type = "S".equalsIgnoreCase(deliveredType) ? ShippingDeliveryType.BRANCH : ShippingDeliveryType.HOME;
                String serviceCode = productType + "_" + (type == ShippingDeliveryType.BRANCH ? "BRANCH" : "HOME");
                quotes.add(new ShippingProviderQuote(ShippingProviderType.CORREO_ARGENTINO, serviceCode,
                        "Correo Argentino · " + productName + (type == ShippingDeliveryType.BRANCH ? " · Sucursal" : " · Domicilio"),
                        type, price, estimatedMin(productName), estimatedMax(productName), estimatedText(productName), null, rate.toString()));
            }
            return quotes;
        } catch (RestClientResponseException ex) {
            throw new BusinessException("Correo Argentino no pudo cotizar el envío (HTTP " + ex.getStatusCode().value() + ").");
        }
    }

    private int cm(BigDecimal value) { return Math.max(1, value.setScale(0, RoundingMode.CEILING).intValue()); }
    private String normalizePostal(String value) { return value == null ? "" : value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT); }
    private Integer estimatedMin(String name) { String n=name.toLowerCase(Locale.ROOT); return n.contains("expreso")?1:n.contains("hoy")?0:2; }
    private Integer estimatedMax(String name) { String n=name.toLowerCase(Locale.ROOT); return n.contains("expreso")?3:n.contains("hoy")?0:5; }
    private String estimatedText(String name) { String n=name.toLowerCase(Locale.ROOT); return n.contains("expreso")?"1 a 3 días hábiles":n.contains("hoy")?"Entrega en el día":"2 a 5 días hábiles"; }
}
