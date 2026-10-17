package com.rodrilang.librarymanager.shipping.correoargentino;

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
public class CorreoArgentinoSettingsReader {
    private static final TypeReference<Map<String,Object>> TYPE = new TypeReference<>(){};
    private final BookstoreShippingIntegrationRepository repository;
    private final ObjectMapper objectMapper;
    public Map<String,Object> settings(Long bookstoreId) {
        BookstoreShippingIntegration i = repository.findByBookstoreIdAndProvider(bookstoreId, ShippingProviderType.CORREO_ARGENTINO).orElse(null);
        if (i == null || i.getSettingsJson() == null || i.getSettingsJson().isBlank()) return Map.of();
        try { return objectMapper.readValue(i.getSettingsJson(), TYPE); } catch (Exception e) { return Map.of(); }
    }
}
