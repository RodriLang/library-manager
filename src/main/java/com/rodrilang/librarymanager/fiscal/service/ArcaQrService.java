package com.rodrilang.librarymanager.fiscal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rodrilang.librarymanager.fiscal.model.BookstoreFiscalSettings;
import com.rodrilang.librarymanager.fiscal.model.FiscalDocument;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ArcaQrService {

    private static final String QR_BASE_URL = "https://www.arca.gob.ar/fe/qr/?p=";

    private final ObjectMapper objectMapper;

    public String buildUrl(FiscalDocument document, BookstoreFiscalSettings settings) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("ver", 1);
        data.put("fecha", document.getIssueDate().toString());
        data.put("cuit", Long.parseLong(settings.getCuit()));
        data.put("ptoVta", document.getPointOfSale());
        data.put("tipoCmp", document.getVoucherTypeCode());
        data.put("nroCmp", document.getVoucherNumber());
        data.put("importe", document.getTotalAmount());
        data.put("moneda", document.getCurrency());
        data.put("ctz", normalizeExchangeRate(document.getExchangeRate()));

        if (document.getRecipientDocumentNumber() != null && !document.getRecipientDocumentNumber().isBlank()) {
            data.put("tipoDocRec", document.getRecipientDocumentTypeCode());
            data.put("nroDocRec", Long.parseLong(document.getRecipientDocumentNumber()));
        }

        data.put("tipoCodAut", "E");
        data.put("codAut", Long.parseLong(document.getCae()));

        try {
            String json = objectMapper.writeValueAsString(data);
            String base64 = Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return QR_BASE_URL + base64;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("No se pudo generar el QR fiscal.", exception);
        }
    }

    private BigDecimal normalizeExchangeRate(BigDecimal value) {
        return value == null ? BigDecimal.ONE : value.stripTrailingZeros();
    }
}
