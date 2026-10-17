package com.rodrilang.librarymanager.shipping.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.repository.BookstoreRepository;
import com.rodrilang.librarymanager.shipping.crypto.ShippingSecretCipher;
import com.rodrilang.librarymanager.shipping.dto.UpdateShippingIntegrationRequest;
import com.rodrilang.librarymanager.shipping.model.BookstoreShippingSettings;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import com.rodrilang.librarymanager.shipping.provider.DefaultShippingProviderRegistry;
import com.rodrilang.librarymanager.shipping.repository.BookstoreShippingIntegrationRepository;
import com.rodrilang.librarymanager.shipping.repository.BookstoreShippingSettingsRepository;
import com.rodrilang.librarymanager.shipping.repository.BookstoreShippingServiceRepository;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.mockito.ArgumentCaptor;
import com.rodrilang.librarymanager.shipping.model.BookstoreShippingIntegration;

class BookstoreShippingConfigurationServiceTest {
    @Test
    void doesNotEnableCarrierWithoutCredentials() {
        BookstoreShippingSettingsRepository settings = mock(BookstoreShippingSettingsRepository.class);
        BookstoreShippingIntegrationRepository integrations = mock(BookstoreShippingIntegrationRepository.class);
        BookstoreShippingServiceRepository shippingServices = mock(BookstoreShippingServiceRepository.class);
        BookstoreRepository bookstores = mock(BookstoreRepository.class);
        BookstoreContext context = mock(BookstoreContext.class);
        ShippingSecretCipher cipher = mock(ShippingSecretCipher.class);
        ObjectMapper mapper = new ObjectMapper();
        BookstoreShippingConfigurationService service = new BookstoreShippingConfigurationService(
                settings, integrations, shippingServices, bookstores, context, new DefaultShippingProviderRegistry(), cipher, mapper
        );

        when(context.getCurrentBookstoreId()).thenReturn(1L);
        when(integrations.findByBookstoreIdAndProvider(1L, ShippingProviderType.ANDREANI)).thenReturn(Optional.empty());
        when(bookstores.findById(1L)).thenReturn(Optional.of(Bookstore.builder().id(1L).name("Librería").build()));

        assertThrows(BusinessException.class, () -> service.updateIntegration(
                ShippingProviderType.ANDREANI,
                new UpdateShippingIntegrationRequest(true, null, Map.of())
        ));
        verify(integrations, never()).save(any());
    }

    @Test
    void doesNotEnableCarrierWithoutCompleteOrigin() {
        BookstoreShippingSettingsRepository settings = mock(BookstoreShippingSettingsRepository.class);
        BookstoreShippingIntegrationRepository integrations = mock(BookstoreShippingIntegrationRepository.class);
        BookstoreShippingServiceRepository shippingServices = mock(BookstoreShippingServiceRepository.class);
        BookstoreRepository bookstores = mock(BookstoreRepository.class);
        BookstoreContext context = mock(BookstoreContext.class);
        ShippingSecretCipher cipher = mock(ShippingSecretCipher.class);
        ObjectMapper mapper = new ObjectMapper();
        BookstoreShippingConfigurationService service = new BookstoreShippingConfigurationService(
                settings, integrations, shippingServices, bookstores, context, new DefaultShippingProviderRegistry(), cipher, mapper
        );

        when(context.getCurrentBookstoreId()).thenReturn(1L);
        when(integrations.findByBookstoreIdAndProvider(1L, ShippingProviderType.ANDREANI)).thenReturn(Optional.empty());
        when(bookstores.findById(1L)).thenReturn(Optional.of(Bookstore.builder().id(1L).name("Librería").build()));
        when(cipher.isConfigured()).thenReturn(true);
        when(cipher.encrypt(anyString())).thenReturn("encrypted");
        when(settings.findByBookstoreId(1L)).thenReturn(Optional.of(BookstoreShippingSettings.builder()
                .bookstore(Bookstore.builder().id(1L).name("Librería").build())
                .originCountryCode("AR")
                .build()));

        assertThrows(BusinessException.class, () -> service.updateIntegration(
                ShippingProviderType.ANDREANI,
                new UpdateShippingIntegrationRequest(true, Map.of("token", "secret"), Map.of())
        ));
        verify(integrations, never()).save(any());
    }
    @Test
    void movesOcaAccountDataFromCredentialsToSettingsAndKeepsOnlySecretsEncrypted() {
        BookstoreShippingSettingsRepository settings = mock(BookstoreShippingSettingsRepository.class);
        BookstoreShippingIntegrationRepository integrations = mock(BookstoreShippingIntegrationRepository.class);
        BookstoreShippingServiceRepository shippingServices = mock(BookstoreShippingServiceRepository.class);
        BookstoreRepository bookstores = mock(BookstoreRepository.class);
        BookstoreContext context = mock(BookstoreContext.class);
        ShippingSecretCipher cipher = mock(ShippingSecretCipher.class);
        ObjectMapper mapper = new ObjectMapper();
        BookstoreShippingConfigurationService service = new BookstoreShippingConfigurationService(
                settings, integrations, shippingServices, bookstores, context, new DefaultShippingProviderRegistry(), cipher, mapper
        );

        when(context.getCurrentBookstoreId()).thenReturn(1L);
        when(integrations.findByBookstoreIdAndProvider(1L, ShippingProviderType.OCA)).thenReturn(Optional.empty());
        when(bookstores.findById(1L)).thenReturn(Optional.of(Bookstore.builder().id(1L).name("Librería").build()));
        when(cipher.isConfigured()).thenReturn(true);
        when(cipher.encrypt(anyString())).thenReturn("encrypted");
        when(cipher.decrypt("encrypted")).thenReturn("{\"usuario\":\"test@oca.com.ar\",\"password\":\"123456\"}");
        when(integrations.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(shippingServices.findAllByBookstoreIdAndProviderOrderBySortOrderAscIdAsc(1L, ShippingProviderType.OCA)).thenReturn(java.util.List.of());

        var response = service.updateIntegration(ShippingProviderType.OCA, new UpdateShippingIntegrationRequest(
                false,
                Map.of(
                        "usuario", "test@oca.com.ar",
                        "password", "123456",
                        "cuit", "30-53625919-4",
                        "nroCuenta", "111757/001",
                        "operativa", "64665",
                        "centroCosto", "1"
                ),
                Map.of("environment", "QA")
        ));

        ArgumentCaptor<String> encryptedPayload = ArgumentCaptor.forClass(String.class);
        verify(cipher).encrypt(encryptedPayload.capture());
        assertEquals(Map.of("usuario", "test@oca.com.ar", "password", "123456"),
                assertDoesNotThrow(() -> mapper.readValue(encryptedPayload.getValue(), new com.fasterxml.jackson.core.type.TypeReference<Map<String,String>>() {})));
        assertEquals("30-53625919-4", response.settings().get("cuit"));
        assertEquals("111757/001", response.settings().get("nroCuenta"));
        assertEquals("64665", response.settings().get("operativa"));
        assertEquals("1", response.settings().get("centroCosto"));
        assertEquals("QA", response.settings().get("environment"));
        assertEquals(java.util.List.of("password", "usuario"), response.credentialKeys());
    }

}
