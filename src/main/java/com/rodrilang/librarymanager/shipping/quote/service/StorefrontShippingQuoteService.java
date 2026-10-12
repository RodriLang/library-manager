package com.rodrilang.librarymanager.shipping.quote.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPrice;
import com.rodrilang.librarymanager.inventory.pricing.service.InventoryPriceService;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import com.rodrilang.librarymanager.shipping.dto.ShippingServiceResponse;
import com.rodrilang.librarymanager.shipping.model.*;
import com.rodrilang.librarymanager.shipping.quote.dto.*;
import com.rodrilang.librarymanager.shipping.quote.model.StoreShippingQuote;
import com.rodrilang.librarymanager.shipping.quote.provider.*;
import com.rodrilang.librarymanager.shipping.quote.repository.StoreShippingQuoteRepository;
import com.rodrilang.librarymanager.shipping.repository.BookstoreShippingIntegrationRepository;
import com.rodrilang.librarymanager.shipping.repository.BookstoreShippingServiceRepository;
import com.rodrilang.librarymanager.shipping.repository.BookstoreShippingSettingsRepository;
import com.rodrilang.librarymanager.shipping.service.BookstoreShippingConfigurationService;
import com.rodrilang.librarymanager.store.channel.SalesChannelType;
import com.rodrilang.librarymanager.store.model.BookstoreStore;
import com.rodrilang.librarymanager.store.model.StorePublication;
import com.rodrilang.librarymanager.store.repository.BookstoreStoreRepository;
import com.rodrilang.librarymanager.store.repository.StorePublicationRepository;
import com.rodrilang.librarymanager.store.service.SalesChannelService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StorefrontShippingQuoteService {
    private static final BigDecimal ZERO = new BigDecimal("0.00");
    private static final BigDecimal DEFAULT_WEIGHT_GRAMS = new BigDecimal("400");
    private static final BigDecimal DEFAULT_WIDTH_CM = new BigDecimal("17");
    private static final BigDecimal DEFAULT_HEIGHT_CM = new BigDecimal("24");
    private static final BigDecimal DEFAULT_DEPTH_CM = new BigDecimal("2.5");
    private static final TypeReference<Map<String, Object>> OBJECT_MAP = new TypeReference<>() {};

    private final BookstoreStoreRepository storeRepository;
    private final StorePublicationRepository publicationRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryPriceService priceService;
    private final SalesChannelService salesChannelService;
    private final BookstoreShippingSettingsRepository settingsRepository;
    private final BookstoreShippingIntegrationRepository integrationRepository;
    private final BookstoreShippingServiceRepository shippingServiceRepository;
    private final BookstoreShippingConfigurationService configurationService;
    private final StoreShippingQuoteRepository quoteRepository;
    private final AndreaniQuoteClient andreaniClient;
    private final ObjectMapper objectMapper;

    @Value("${app.store.shipping.quote-ttl-minutes:10}")
    private long quoteTtlMinutes;

    @Transactional
    public StorefrontShippingQuotesResponse quote(UUID publicStoreId, StorefrontShippingQuoteRequest request) {
        BookstoreStore store = requireActiveStore(publicStoreId);
        Long bookstoreId = store.getBookstore().getId();
        String postalCode = normalizePostalCode(request.postalCode());
        Map<Long, Integer> requested = aggregate(request.items());
        List<Long> ids = requested.keySet().stream().sorted().toList();

        List<Inventory> inventories = inventoryRepository.findAllByBookstoreIdAndIdIn(bookstoreId, ids);
        if (inventories.size() != ids.size()) throw new BusinessException("Uno o más productos ya no están disponibles.");
        Map<Long, Inventory> inventoryById = inventories.stream().collect(Collectors.toMap(Inventory::getId, Function.identity()));
        Map<Long, StorePublication> publications = publicationRepository.findAllByStoreIdAndInventoryIdIn(store.getId(), ids).stream()
                .collect(Collectors.toMap(p -> p.getInventory().getId(), Function.identity()));
        Map<Long, InventoryPrice> prices = priceService.currentFor(ids);

        BigDecimal subtotal = ZERO;
        for (Long id : ids) {
            Inventory inventory = inventoryById.get(id);
            StorePublication publication = publications.get(id);
            if (publication == null || !Boolean.TRUE.equals(publication.getPublished()) || !Boolean.TRUE.equals(inventory.getActive())) {
                throw new BusinessException("Uno de los productos ya no está publicado en la tienda.");
            }
            InventoryPrice price = prices.get(id);
            if (price == null || price.getAmount() == null) throw new BusinessException("Uno de los productos no tiene precio vigente.");
            subtotal = subtotal.add(price.getAmount().multiply(BigDecimal.valueOf(requested.get(id))));
        }
        subtotal = money(subtotal);

        ShippingPackage pack = estimatePackage(ids, requested, inventoryById, subtotal);
        var settings = settingsRepository.findByBookstoreId(bookstoreId).orElse(null);
        BigDecimal surcharge = settings == null || settings.getPackagingSurcharge() == null ? ZERO : money(settings.getPackagingSurcharge());
        boolean freeShipping = settings != null && settings.getFreeShippingThreshold() != null
                && subtotal.compareTo(settings.getFreeShippingThreshold()) >= 0;
        String itemHash = itemHash(requested);
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(Math.max(1, quoteTtlMinutes)));

        List<StorefrontShippingQuoteResponse> results = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        addPickupQuotes(store, bookstoreId, postalCode, itemHash, pack, subtotal, expiresAt, results);
        addManualQuotes(store, bookstoreId, postalCode, itemHash, pack, subtotal, surcharge, freeShipping, expiresAt, results);
        addAndreaniQuotes(store, bookstoreId, postalCode, itemHash, pack, surcharge, freeShipping, expiresAt, results, warnings);

        results.sort(Comparator.comparing(StorefrontShippingQuoteResponse::amount)
                .thenComparing(StorefrontShippingQuoteResponse::displayName));
        return new StorefrontShippingQuotesResponse(
                postalCode,
                new ShippingPackageEstimateResponse(pack.weightGrams(), pack.widthCm(), pack.heightCm(), pack.depthCm(), pack.volumeCm3(), pack.usedFallbackValues()),
                List.copyOf(results),
                List.copyOf(warnings)
        );
    }

    @Transactional(readOnly = true)
    public StoreShippingQuote requireValidQuote(Long storeId, UUID quoteId, String postalCode, List<com.rodrilang.librarymanager.store.order.dto.CreateStoreOrderItemRequest> items) {
        StoreShippingQuote quote = quoteRepository.findByIdAndStoreId(quoteId, storeId)
                .orElseThrow(() -> new BusinessException("La cotización de envío ya no está disponible. Volvé a cotizar."));
        if (quote.getExpiresAt().isBefore(Instant.now())) throw new BusinessException("La cotización de envío venció. Volvé a cotizar.");
        if (!quote.getDestinationPostalCode().equals(normalizePostalCode(postalCode))) throw new BusinessException("La cotización no corresponde al código postal indicado.");
        Map<Long, Integer> requested = new LinkedHashMap<>();
        for (var item : items) requested.merge(item.inventoryId(), item.quantity(), Integer::sum);
        if (!quote.getItemsHash().equals(itemHash(requested))) throw new BusinessException("El carrito cambió desde la cotización. Volvé a cotizar el envío.");
        return quote;
    }

    private void addAndreaniQuotes(BookstoreStore store, Long bookstoreId, String postalCode, String itemHash, ShippingPackage pack,
                                   BigDecimal surcharge, boolean freeShipping, Instant expiresAt,
                                   List<StorefrontShippingQuoteResponse> results, List<String> warnings) {
        var integration = integrationRepository.findByBookstoreIdAndProvider(bookstoreId, ShippingProviderType.ANDREANI).orElse(null);
        if (integration == null || !Boolean.TRUE.equals(integration.getEnabled())) return;
        boolean homeEnabled = shippingServiceRepository.findAllByBookstoreIdAndProviderOrderBySortOrderAscIdAsc(bookstoreId, ShippingProviderType.ANDREANI)
                .stream().filter(s -> s.getDeliveryType() == ShippingDeliveryType.HOME).allMatch(s -> Boolean.TRUE.equals(s.getEnabled()));
        if (!homeEnabled) return;
        try {
            Map<String, String> credentials = configurationService.requireCredentials(bookstoreId, ShippingProviderType.ANDREANI);
            Map<String, Object> integrationSettings = readSettings(integration.getSettingsJson());
            ShippingProviderQuote providerQuote = andreaniClient.quoteHome(postalCode, pack, credentials, integrationSettings);
            results.add(persist(store, postalCode, itemHash, pack, surcharge, freeShipping, expiresAt, providerQuote));
        } catch (RuntimeException ex) {
            warnings.add("Andreani no pudo cotizar en este momento.");
        }
    }

    private void addPickupQuotes(BookstoreStore store, Long bookstoreId, String postalCode, String itemHash, ShippingPackage pack,
                                 BigDecimal subtotal, Instant expiresAt, List<StorefrontShippingQuoteResponse> results) {
        var integration = integrationRepository.findByBookstoreIdAndProvider(bookstoreId, ShippingProviderType.PICKUP_IN_STORE).orElse(null);
        if (integration == null || !Boolean.TRUE.equals(integration.getEnabled())) return;
        List<com.rodrilang.librarymanager.shipping.model.BookstoreShippingService> services = shippingServiceRepository
                .findAllByBookstoreIdAndProviderOrderBySortOrderAscIdAsc(bookstoreId, ShippingProviderType.PICKUP_IN_STORE);
        if (services.isEmpty()) {
            ShippingProviderQuote q = new ShippingProviderQuote(ShippingProviderType.PICKUP_IN_STORE, "STORE_PICKUP", "Retiro en librería",
                    ShippingDeliveryType.STORE_PICKUP, ZERO, null, null, "Sin costo", null, null);
            results.add(persist(store, postalCode, itemHash, pack, ZERO, false, expiresAt, q));
            return;
        }
        for (var service : services) {
            if (!Boolean.TRUE.equals(service.getEnabled())) continue;
            ShippingProviderQuote q = new ShippingProviderQuote(ShippingProviderType.PICKUP_IN_STORE, service.getServiceCode(), service.getDisplayName(),
                    ShippingDeliveryType.STORE_PICKUP, ZERO, null, null, settingText(service.getSettingsJson(), "estimatedDays"), null, service.getSettingsJson());
            results.add(persist(store, postalCode, itemHash, pack, ZERO, false, expiresAt, q));
        }
    }

    private void addManualQuotes(BookstoreStore store, Long bookstoreId, String postalCode, String itemHash, ShippingPackage pack,
                                 BigDecimal subtotal, BigDecimal surcharge, boolean freeShipping, Instant expiresAt,
                                 List<StorefrontShippingQuoteResponse> results) {
        var integration = integrationRepository.findByBookstoreIdAndProvider(bookstoreId, ShippingProviderType.MANUAL).orElse(null);
        if (integration == null || !Boolean.TRUE.equals(integration.getEnabled())) return;
        for (var service : shippingServiceRepository.findAllByBookstoreIdAndProviderOrderBySortOrderAscIdAsc(bookstoreId, ShippingProviderType.MANUAL)) {
            if (!Boolean.TRUE.equals(service.getEnabled())) continue;
            Map<String, Object> settings = readSettings(service.getSettingsJson());
            if (!postalAllowed(postalCode, settings.get("postalCodes"))) continue;
            BigDecimal minOrder = decimal(settings.get("minOrderAmount"), ZERO);
            if (subtotal.compareTo(minOrder) < 0) continue;
            BigDecimal price = decimal(settings.get("price"), ZERO);
            ShippingProviderQuote q = new ShippingProviderQuote(ShippingProviderType.MANUAL, service.getServiceCode(), service.getDisplayName(),
                    service.getDeliveryType(), price, null, null, objectText(settings.get("estimatedDays")), null, service.getSettingsJson());
            results.add(persist(store, postalCode, itemHash, pack, surcharge, freeShipping, expiresAt, q));
        }
    }

    private StorefrontShippingQuoteResponse persist(BookstoreStore store, String postalCode, String itemHash, ShippingPackage pack,
                                                    BigDecimal surcharge, boolean freeShipping, Instant expiresAt, ShippingProviderQuote providerQuote) {
        BigDecimal providerAmount = money(providerQuote.amount());
        BigDecimal charged = freeShipping && providerQuote.deliveryType() != ShippingDeliveryType.STORE_PICKUP
                ? ZERO : money(providerAmount.add(surcharge));
        StoreShippingQuote entity = quoteRepository.save(StoreShippingQuote.builder()
                .id(UUID.randomUUID()).store(store).bookstore(store.getBookstore()).provider(providerQuote.provider())
                .serviceCode(providerQuote.serviceCode()).serviceName(providerQuote.displayName()).deliveryType(providerQuote.deliveryType())
                .destinationPostalCode(postalCode).itemsHash(itemHash).providerAmount(providerAmount).packagingSurcharge(surcharge)
                .chargedAmount(charged).currency("ARS").estimatedDaysMin(providerQuote.estimatedDaysMin()).estimatedDaysMax(providerQuote.estimatedDaysMax())
                .estimatedText(providerQuote.estimatedText()).externalQuoteId(providerQuote.externalQuoteId()).freeShippingApplied(freeShipping)
                .weightGrams(pack.weightGrams()).widthCm(pack.widthCm()).heightCm(pack.heightCm()).depthCm(pack.depthCm()).volumeCm3(pack.volumeCm3())
                .metadataJson(providerQuote.metadataJson()).expiresAt(expiresAt).createdAt(Instant.now()).build());
        return new StorefrontShippingQuoteResponse(entity.getId(), entity.getProvider(), entity.getServiceCode(), entity.getServiceName(), entity.getDeliveryType(),
                entity.getChargedAmount(), entity.getCurrency(), entity.getEstimatedDaysMin(), entity.getEstimatedDaysMax(), entity.getEstimatedText(),
                Boolean.TRUE.equals(entity.getFreeShippingApplied()), entity.getExpiresAt());
    }

    private ShippingPackage estimatePackage(List<Long> ids, Map<Long, Integer> quantities, Map<Long, Inventory> inventories, BigDecimal subtotal) {
        BigDecimal weight = new BigDecimal("150");
        BigDecimal width = BigDecimal.ZERO;
        BigDecimal height = BigDecimal.ZERO;
        BigDecimal depth = new BigDecimal("2");
        boolean fallback = false;
        for (Long id : ids) {
            Book book = inventories.get(id).getBook();
            int quantity = quantities.get(id);
            BigDecimal bookWeight = positive(book.getWeightGrams(), DEFAULT_WEIGHT_GRAMS); fallback |= book.getWeightGrams() == null;
            BigDecimal bookWidth = positive(book.getWidthCm(), DEFAULT_WIDTH_CM); fallback |= book.getWidthCm() == null;
            BigDecimal bookHeight = positive(book.getHeightCm(), DEFAULT_HEIGHT_CM); fallback |= book.getHeightCm() == null;
            BigDecimal bookDepth = positive(book.getDepthCm(), DEFAULT_DEPTH_CM); fallback |= book.getDepthCm() == null;
            weight = weight.add(bookWeight.multiply(BigDecimal.valueOf(quantity)));
            width = width.max(bookWidth);
            height = height.max(bookHeight);
            depth = depth.add(bookDepth.multiply(BigDecimal.valueOf(quantity)));
        }
        width = width.add(new BigDecimal("2")).setScale(2, RoundingMode.CEILING);
        height = height.add(new BigDecimal("2")).setScale(2, RoundingMode.CEILING);
        depth = depth.max(new BigDecimal("5")).setScale(2, RoundingMode.CEILING);
        int grams = weight.setScale(0, RoundingMode.CEILING).intValue();
        BigDecimal volume = width.multiply(height).multiply(depth).setScale(2, RoundingMode.CEILING);
        return new ShippingPackage(grams, width, height, depth, volume, subtotal, fallback);
    }

    private BookstoreStore requireActiveStore(UUID publicStoreId) {
        BookstoreStore store = storeRepository.findByPublicId(publicStoreId).orElseThrow(() -> new ResourceNotFoundException("No se encontró la tienda."));
        if (!Boolean.TRUE.equals(store.getBookstore().getActive()) || !salesChannelService.isEnabled(store.getBookstore().getId(), SalesChannelType.ANAQUEL_STORE)) {
            throw new ResourceNotFoundException("La tienda no está disponible.");
        }
        return store;
    }

    private Map<Long, Integer> aggregate(List<StorefrontShippingQuoteItemRequest> items) {
        Map<Long, Integer> result = new LinkedHashMap<>();
        for (var item : items) result.merge(item.inventoryId(), item.quantity(), Integer::sum);
        return result;
    }

    private String itemHash(Map<Long, Integer> requested) {
        String canonical = requested.entrySet().stream().sorted(Map.Entry.comparingByKey())
                .map(e -> e.getKey() + ":" + e.getValue()).collect(Collectors.joining("|"));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception ex) { throw new IllegalStateException(ex); }
    }

    private Map<String, Object> readSettings(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try { return objectMapper.readValue(json, OBJECT_MAP); }
        catch (Exception ex) { return Map.of(); }
    }

    private String settingText(String json, String key) { return objectText(readSettings(json).get(key)); }
    private String objectText(Object value) { return value == null ? null : String.valueOf(value).trim(); }
    private BigDecimal decimal(Object value, BigDecimal fallback) {
        if (value == null) return fallback;
        try { return new BigDecimal(String.valueOf(value)); } catch (NumberFormatException ex) { return fallback; }
    }
    private boolean postalAllowed(String postalCode, Object configured) {
        if (!(configured instanceof Collection<?> values) || values.isEmpty()) return true;
        return values.stream().map(String::valueOf).map(this::normalizePostalCode).anyMatch(postalCode::equals);
    }
    private BigDecimal positive(BigDecimal value, BigDecimal fallback) { return value != null && value.signum() > 0 ? value : fallback; }
    private String normalizePostalCode(String value) { return value == null ? "" : value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT); }
    private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
}
