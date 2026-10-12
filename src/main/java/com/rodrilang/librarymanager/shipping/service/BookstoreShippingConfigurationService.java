package com.rodrilang.librarymanager.shipping.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.repository.BookstoreRepository;
import com.rodrilang.librarymanager.shipping.crypto.ShippingSecretCipher;
import com.rodrilang.librarymanager.shipping.dto.*;
import com.rodrilang.librarymanager.shipping.model.*;
import com.rodrilang.librarymanager.shipping.provider.DefaultShippingProviderRegistry;
import com.rodrilang.librarymanager.shipping.provider.ShippingProvider;
import com.rodrilang.librarymanager.shipping.repository.BookstoreShippingIntegrationRepository;
import com.rodrilang.librarymanager.shipping.repository.BookstoreShippingSettingsRepository;
import com.rodrilang.librarymanager.shipping.repository.BookstoreShippingServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class BookstoreShippingConfigurationService {
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};
    private static final Set<String> OCA_NON_SECRET_CREDENTIAL_KEYS = Set.of(
            "cuit", "nroCuenta", "operativa", "operativaSucursal", "operativaDevolucion",
            "operativaLogisticaInversa", "centroCosto"
    );

    private final BookstoreShippingSettingsRepository settingsRepository;
    private final BookstoreShippingIntegrationRepository integrationRepository;
    private final BookstoreShippingServiceRepository shippingServiceRepository;
    private final BookstoreRepository bookstoreRepository;
    private final BookstoreContext bookstoreContext;
    private final DefaultShippingProviderRegistry providerRegistry;
    private final ShippingSecretCipher cipher;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ShippingConfigurationResponse current() {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        BookstoreShippingSettings settings = settingsRepository.findByBookstoreId(bookstoreId).orElse(null);
        Map<ShippingProviderType, BookstoreShippingIntegration> integrations = new EnumMap<>(ShippingProviderType.class);
        integrationRepository.findAllByBookstoreId(bookstoreId).forEach(i -> integrations.put(i.getProvider(), i));
        Map<ShippingProviderType, List<BookstoreShippingService>> services = new EnumMap<>(ShippingProviderType.class);
        shippingServiceRepository.findAllByBookstoreIdOrderBySortOrderAscIdAsc(bookstoreId)
                .forEach(service -> services.computeIfAbsent(service.getProvider(), ignored -> new ArrayList<>()).add(service));
        return new ShippingConfigurationResponse(
                toSettingsResponse(settings),
                providerRegistry.all().stream()
                        .map(p -> toIntegrationResponse(p, integrations.get(p.type()), services.getOrDefault(p.type(), List.of())))
                        .toList()
        );
    }

    @Transactional
    public ShippingSettingsResponse updateSettings(UpdateShippingSettingsRequest request) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        BookstoreShippingSettings settings = settingsRepository.findByBookstoreId(bookstoreId)
                .orElseGet(() -> newSettings(bookstoreId));

        settings.setOriginName(clean(request.originName()));
        settings.setOriginContactName(clean(request.originContactName()));
        settings.setOriginEmail(clean(request.originEmail()));
        settings.setOriginPhone(clean(request.originPhone()));
        settings.setOriginStreet(clean(request.originStreet()));
        settings.setOriginStreetNumber(clean(request.originStreetNumber()));
        settings.setOriginFloorApartment(clean(request.originFloorApartment()));
        settings.setOriginPostalCode(clean(request.originPostalCode()));
        settings.setOriginCity(clean(request.originCity()));
        settings.setOriginState(clean(request.originState()));
        settings.setOriginCountryCode(clean(request.originCountryCode()) == null ? "AR" : request.originCountryCode().trim().toUpperCase(Locale.ROOT));
        settings.setPreparationDays(request.preparationDays() == null ? 1 : request.preparationDays());
        settings.setFreeShippingThreshold(request.freeShippingThreshold());
        settings.setPackagingSurcharge(request.packagingSurcharge() == null ? BigDecimal.ZERO : request.packagingSurcharge());

        return toSettingsResponse(settingsRepository.save(settings));
    }

    @Transactional
    public ShippingIntegrationResponse updateIntegration(ShippingProviderType providerType, UpdateShippingIntegrationRequest request) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        ShippingProvider provider = providerRegistry.get(providerType);
        BookstoreShippingIntegration integration = integrationRepository.findByBookstoreIdAndProvider(bookstoreId, providerType)
                .orElseGet(() -> newIntegration(bookstoreId, providerType));

        Map<String, Object> migratedSettings = new LinkedHashMap<>();
        if (request.credentials() != null) {
            Map<String, String> credentials = normalizeCredentials(request.credentials());
            if (providerType == ShippingProviderType.OCA) {
                OCA_NON_SECRET_CREDENTIAL_KEYS.forEach(key -> {
                    String value = credentials.remove(key);
                    if (value != null) migratedSettings.put(key, value);
                });
            }
            if (credentials.isEmpty()) {
                integration.setCredentialsEncrypted(null);
            } else {
                if (!cipher.isConfigured()) {
                    throw new BusinessException("Configurá la clave de cifrado de envíos antes de guardar credenciales.");
                }
                integration.setCredentialsEncrypted(cipher.encrypt(write(credentials)));
            }
        }

        if (request.settings() != null || !migratedSettings.isEmpty()) {
            Map<String, Object> settings = request.settings() == null
                    ? new LinkedHashMap<>(readSettings(integration.getSettingsJson()))
                    : new LinkedHashMap<>(normalizeSettings(request.settings()));
            settings.putAll(migratedSettings);
            integration.setSettingsJson(write(settings));
        }

        boolean enabled = request.enabled() == null ? Boolean.TRUE.equals(integration.getEnabled()) : request.enabled();
        boolean credentialsConfigured = hasText(integration.getCredentialsEncrypted());

        if (enabled && provider.requiresCredentials() && !credentialsConfigured) {
            throw new BusinessException("Cargá las credenciales de " + provider.displayName() + " antes de habilitar la integración.");
        }
        if (enabled && provider.requiresCredentials() && !hasCompleteOrigin(bookstoreId)) {
            throw new BusinessException("Completá el origen de los envíos antes de habilitar " + provider.displayName() + ".");
        }

        integration.setEnabled(enabled);
        integration.setLastError(null);
        if (!provider.requiresCredentials() || credentialsConfigured) {
            integration.setStatus(ShippingIntegrationStatus.CONFIGURED);
            if (integration.getConfiguredAt() == null) integration.setConfiguredAt(Instant.now());
        } else {
            integration.setStatus(ShippingIntegrationStatus.NOT_CONFIGURED);
            integration.setConfiguredAt(null);
        }

        return toIntegrationResponse(provider, integrationRepository.save(integration), shippingServiceRepository.findAllByBookstoreIdAndProviderOrderBySortOrderAscIdAsc(bookstoreId, providerType));
    }

    @Transactional
    public ShippingIntegrationResponse disconnect(ShippingProviderType providerType) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        ShippingProvider provider = providerRegistry.get(providerType);
        BookstoreShippingIntegration integration = integrationRepository.findByBookstoreIdAndProvider(bookstoreId, providerType)
                .orElseGet(() -> newIntegration(bookstoreId, providerType));
        integration.setEnabled(false);
        integration.setCredentialsEncrypted(null);
        integration.setLastError(null);
        integration.setConfiguredAt(null);
        integration.setStatus(provider.requiresCredentials() ? ShippingIntegrationStatus.NOT_CONFIGURED : ShippingIntegrationStatus.CONFIGURED);
        return toIntegrationResponse(provider, integrationRepository.save(integration), shippingServiceRepository.findAllByBookstoreIdAndProviderOrderBySortOrderAscIdAsc(bookstoreId, providerType));
    }


    @Transactional
    public ShippingServiceResponse updateService(ShippingProviderType providerType, String serviceCode, UpdateShippingServiceRequest request) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        providerRegistry.get(providerType);
        String code = clean(serviceCode);
        if (code == null || code.length() > 100) {
            throw new BusinessException("El código del servicio de envío no es válido.");
        }
        BookstoreShippingService service = shippingServiceRepository
                .findByBookstoreIdAndProviderAndServiceCode(bookstoreId, providerType, code)
                .orElseGet(() -> BookstoreShippingService.builder()
                        .bookstore(bookstore(bookstoreId))
                        .provider(providerType)
                        .serviceCode(code)
                        .build());
        service.setDisplayName(request.displayName().trim());
        service.setDeliveryType(request.deliveryType());
        service.setEnabled(request.enabled() == null || request.enabled());
        service.setSortOrder(request.sortOrder() == null ? 0 : request.sortOrder());
        service.setSettingsJson(request.settings() == null ? service.getSettingsJson() : write(normalizeSettings(request.settings())));
        return toServiceResponse(shippingServiceRepository.save(service));
    }

    @Transactional(readOnly = true)
    public List<ShippingServiceResponse> services(ShippingProviderType providerType) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        providerRegistry.get(providerType);
        return servicesForResponse(providerType, shippingServiceRepository.findAllByBookstoreIdAndProviderOrderBySortOrderAscIdAsc(bookstoreId, providerType));
    }

    @Transactional(readOnly = true)
    public Map<String, String> configuredCredentials(Long bookstoreId, ShippingProviderType providerType) {
        ShippingProvider provider = providerRegistry.get(providerType);
        if (!provider.requiresCredentials()) return Map.of();
        BookstoreShippingIntegration integration = integrationRepository.findByBookstoreIdAndProvider(bookstoreId, providerType)
                .orElseThrow(() -> new BusinessException("La integración de envíos no está configurada."));
        if (!hasText(integration.getCredentialsEncrypted())) {
            throw new BusinessException("Cargá las credenciales de " + provider.displayName() + " antes de probar la conexión.");
        }
        try {
            return objectMapper.readValue(cipher.decrypt(integration.getCredentialsEncrypted()), STRING_MAP);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Las credenciales de envío configuradas no son válidas.");
        }
    }

    @Transactional(readOnly = true)
    public Map<String, String> requireCredentials(Long bookstoreId, ShippingProviderType providerType) {
        ShippingProvider provider = providerRegistry.get(providerType);
        if (!provider.requiresCredentials()) return Map.of();
        BookstoreShippingIntegration integration = integrationRepository.findByBookstoreIdAndProvider(bookstoreId, providerType)
                .orElseThrow(() -> new BusinessException("La integración de envíos no está configurada."));
        if (!Boolean.TRUE.equals(integration.getEnabled()) || !hasText(integration.getCredentialsEncrypted())) {
            throw new BusinessException(provider.displayName() + " no está habilitado para esta librería.");
        }
        try {
            return objectMapper.readValue(cipher.decrypt(integration.getCredentialsEncrypted()), STRING_MAP);
        } catch (JsonProcessingException ex) {
            throw new BusinessException("Las credenciales de envío configuradas no son válidas.");
        }
    }

    private BookstoreShippingSettings newSettings(Long bookstoreId) {
        Bookstore bookstore = bookstore(bookstoreId);
        return BookstoreShippingSettings.builder()
                .bookstore(bookstore)
                .originName(bookstore.getName())
                .originEmail(bookstore.getEmail())
                .originPhone(bookstore.getPhone())
                .originCountryCode("AR")
                .preparationDays(1)
                .packagingSurcharge(BigDecimal.ZERO)
                .build();
    }

    private BookstoreShippingIntegration newIntegration(Long bookstoreId, ShippingProviderType provider) {
        return BookstoreShippingIntegration.builder()
                .bookstore(bookstore(bookstoreId))
                .provider(provider)
                .enabled(false)
                .status(ShippingIntegrationStatus.NOT_CONFIGURED)
                .build();
    }

    private Bookstore bookstore(Long bookstoreId) {
        return bookstoreRepository.findById(bookstoreId)
                .orElseThrow(() -> new BusinessException("No se encontró la librería."));
    }

    private ShippingSettingsResponse toSettingsResponse(BookstoreShippingSettings settings) {
        if (settings == null) {
            return new ShippingSettingsResponse(new ShippingOriginResponse(null, null, null, null, null, null, null, null, null, null, "AR"), 1, null, BigDecimal.ZERO, false);
        }
        ShippingOriginResponse origin = new ShippingOriginResponse(
                settings.getOriginName(), settings.getOriginContactName(), settings.getOriginEmail(), settings.getOriginPhone(),
                settings.getOriginStreet(), settings.getOriginStreetNumber(), settings.getOriginFloorApartment(),
                settings.getOriginPostalCode(), settings.getOriginCity(), settings.getOriginState(), settings.getOriginCountryCode()
        );
        return new ShippingSettingsResponse(origin, settings.getPreparationDays(), settings.getFreeShippingThreshold(), settings.getPackagingSurcharge(), hasCompleteOrigin(settings));
    }

    private ShippingIntegrationResponse toIntegrationResponse(ShippingProvider provider, BookstoreShippingIntegration integration, List<BookstoreShippingService> services) {
        boolean credentialsConfigured = integration != null && hasText(integration.getCredentialsEncrypted());
        return new ShippingIntegrationResponse(
                provider.type(), provider.displayName(), provider.requiresCredentials(), provider.capabilities(),
                integration != null && Boolean.TRUE.equals(integration.getEnabled()),
                integration == null ? ShippingIntegrationStatus.NOT_CONFIGURED : integration.getStatus(),
                credentialsConfigured,
                credentialsConfigured ? credentialKeys(integration) : List.of(),
                integration == null ? Map.of() : integrationSettingsForResponse(provider.type(), integration),
                servicesForResponse(provider.type(), services),
                integration == null ? null : integration.getLastError(),
                integration == null ? null : integration.getConfiguredAt()
        );
    }



    private Map<String, Object> integrationSettingsForResponse(ShippingProviderType providerType, BookstoreShippingIntegration integration) {
        Map<String, Object> settings = new LinkedHashMap<>(readSettings(integration.getSettingsJson()));
        if (providerType != ShippingProviderType.OCA || !hasText(integration.getCredentialsEncrypted())) return settings;
        try {
            Map<String, String> legacyCredentials = objectMapper.readValue(cipher.decrypt(integration.getCredentialsEncrypted()), STRING_MAP);
            OCA_NON_SECRET_CREDENTIAL_KEYS.forEach(key -> {
                String value = legacyCredentials.get(key);
                if (value != null && !value.isBlank()) settings.putIfAbsent(key, value);
            });
        } catch (RuntimeException | JsonProcessingException ignored) {
            // El bloque de settings sigue siendo utilizable aunque una credencial legacy no pueda leerse.
        }
        return settings;
    }

    private List<ShippingServiceResponse> servicesForResponse(ShippingProviderType provider, List<BookstoreShippingService> persisted) {
        List<ShippingServiceResponse> result = new ArrayList<>(persisted.stream().map(this::toServiceResponse).toList());
        Set<ShippingDeliveryType> configuredTypes = persisted.stream().map(BookstoreShippingService::getDeliveryType).collect(java.util.stream.Collectors.toSet());
        if (provider == ShippingProviderType.ANDREANI && !configuredTypes.contains(ShippingDeliveryType.HOME)) {
            result.add(new ShippingServiceResponse("HOME", "Domicilio", ShippingDeliveryType.HOME, true, 0, Map.of()));
        }
        if (provider == ShippingProviderType.CORREO_ARGENTINO) {
            if (!configuredTypes.contains(ShippingDeliveryType.HOME)) result.add(new ShippingServiceResponse("HOME", "Domicilio", ShippingDeliveryType.HOME, true, 0, Map.of()));
            if (!configuredTypes.contains(ShippingDeliveryType.BRANCH)) result.add(new ShippingServiceResponse("BRANCH", "Retiro en sucursal", ShippingDeliveryType.BRANCH, true, 10, Map.of()));
        }
        if (provider == ShippingProviderType.OCA) {
            if (!configuredTypes.contains(ShippingDeliveryType.HOME)) result.add(new ShippingServiceResponse("HOME", "Domicilio", ShippingDeliveryType.HOME, true, 0, Map.of()));
            if (!configuredTypes.contains(ShippingDeliveryType.BRANCH)) result.add(new ShippingServiceResponse("BRANCH", "Retiro en sucursal", ShippingDeliveryType.BRANCH, true, 10, Map.of()));
        }
        return result.stream().sorted(Comparator.comparingInt(ShippingServiceResponse::sortOrder).thenComparing(ShippingServiceResponse::serviceCode)).toList();
    }

    private ShippingServiceResponse toServiceResponse(BookstoreShippingService service) {
        return new ShippingServiceResponse(
                service.getServiceCode(), service.getDisplayName(), service.getDeliveryType(),
                Boolean.TRUE.equals(service.getEnabled()), service.getSortOrder(), readSettings(service.getSettingsJson())
        );
    }

    private List<String> credentialKeys(BookstoreShippingIntegration integration) {
        try {
            Map<String, String> credentials = objectMapper.readValue(cipher.decrypt(integration.getCredentialsEncrypted()), STRING_MAP);
            return credentials.keySet().stream().sorted().toList();
        } catch (RuntimeException | JsonProcessingException ex) {
            return List.of();
        }
    }

    private Map<String, Object> readSettings(String json) {
        if (!hasText(json)) return Map.of();
        try { return objectMapper.readValue(json, OBJECT_MAP); }
        catch (JsonProcessingException ex) { return Map.of(); }
    }

    private boolean hasCompleteOrigin(Long bookstoreId) {
        return settingsRepository.findByBookstoreId(bookstoreId).map(this::hasCompleteOrigin).orElse(false);
    }

    private boolean hasCompleteOrigin(BookstoreShippingSettings settings) {
        return hasText(settings.getOriginStreet()) && hasText(settings.getOriginStreetNumber())
                && hasText(settings.getOriginPostalCode()) && hasText(settings.getOriginCity())
                && hasText(settings.getOriginState()) && hasText(settings.getOriginCountryCode());
    }

    private Map<String, String> normalizeCredentials(Map<String, String> credentials) {
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>();
        credentials.forEach((key, value) -> {
            String k = clean(key);
            String v = clean(value);
            if (k != null && v != null) normalized.put(k, v);
        });
        return normalized;
    }

    private Map<String, Object> normalizeSettings(Map<String, Object> settings) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        settings.forEach((key, value) -> {
            String k = clean(key);
            if (k != null && value != null) normalized.put(k, value);
        });
        return normalized;
    }

    private String write(Object value) {
        try { return objectMapper.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new BusinessException("No pudimos guardar la configuración de envíos."); }
    }

    private String clean(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private boolean hasText(String value) { return value != null && !value.isBlank(); }
}
