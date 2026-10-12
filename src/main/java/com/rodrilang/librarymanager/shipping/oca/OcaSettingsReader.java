package com.rodrilang.librarymanager.shipping.oca;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rodrilang.librarymanager.shipping.model.BookstoreShippingIntegration;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import com.rodrilang.librarymanager.shipping.repository.BookstoreShippingIntegrationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class OcaSettingsReader {
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};
    private final BookstoreShippingIntegrationRepository integrationRepository;
    private final ObjectMapper objectMapper;

    public Map<String, Object> settings(Long bookstoreId) {
        return integrationRepository.findByBookstoreIdAndProvider(bookstoreId, ShippingProviderType.OCA)
                .map(BookstoreShippingIntegration::getSettingsJson)
                .filter(json -> json != null && !json.isBlank())
                .map(this::read)
                .orElse(Map.of());
    }

    private Map<String, Object> read(String json) {
        try { return objectMapper.readValue(json, OBJECT_MAP); }
        catch (Exception ignored) { return Map.of(); }
    }
}
