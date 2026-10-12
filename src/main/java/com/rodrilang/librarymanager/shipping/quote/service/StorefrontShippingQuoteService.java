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
import com.rodrilang.librarymanager.shipping.oca.OcaQuoteClient;
import com.rodrilang.librarymanager.shipping.operations.ShippingPickupPoint;
import com.rodrilang.librarymanager.shipping.operations.ShippingPickupPointClientRegistry;
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
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
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
    private final CorreoArgentinoQuoteClient correoArgentinoClient;
    private final OcaQuoteClient ocaQuoteClient;
    private final ShippingPickupPointClientRegistry pickupPointClientRegistry;
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
        addAndreaniQuotes(store, bookstoreId, postalCode, itemHash, pack, subtotal, surcharge, freeShipping, expiresAt, results, warnings);
        addCorreoArgentinoQuotes(store, bookstoreId, postalCode, itemHash, pack, subtotal, surcharge, freeShipping, expiresAt, results, warnings);
        addOcaQuotes(store, bookstoreId, postalCode, itemHash, pack, subtotal, surcharge, freeShipping, expiresAt, results, warnings);

        results.sort(Comparator.comparing(StorefrontShippingQuoteResponse::recommended).reversed()
                .thenComparingInt(StorefrontShippingQuoteResponse::sortPriority)
                .thenComparing(StorefrontShippingQuoteResponse::amount)
                .thenComparing(StorefrontShippingQuoteResponse::displayName));
        return new StorefrontShippingQuotesResponse(
                postalCode,
                new ShippingPackageEstimateResponse(pack.weightGrams(), pack.widthCm(), pack.heightCm(), pack.depthCm(), pack.volumeCm3(), pack.usedFallbackValues()),
                List.copyOf(results),
                List.copyOf(warnings)
        );
    }


    @Transactional(readOnly = true)
    public List<ShippingPickupPoint> pickupPoints(UUID publicStoreId, ShippingProviderType provider, String postalCode) {
        BookstoreStore store = requireActiveStore(publicStoreId);
        if (provider == null || provider == ShippingProviderType.MANUAL || provider == ShippingProviderType.PICKUP_IN_STORE) {
            throw new BusinessException("El proveedor seleccionado no ofrece puntos de retiro.");
        }
        Long bookstoreId = store.getBookstore().getId();
        var integration = integrationRepository.findByBookstoreIdAndProvider(bookstoreId, provider).orElse(null);
        if (integration == null || !Boolean.TRUE.equals(integration.getEnabled())) {
            throw new BusinessException("El proveedor de envío no está habilitado para esta tienda.");
        }
        String normalized = normalizePostalCode(postalCode);
        if (normalized.isBlank()) throw new BusinessException("Ingresá un código postal válido.");
        return pickupPointClientRegistry.require(provider).findByPostalCode(bookstoreId, normalized);
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
                                   BigDecimal subtotal, BigDecimal surcharge, boolean freeShipping, Instant expiresAt,
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
            addIfPresent(results, persist(store, postalCode, itemHash, pack, subtotal, surcharge, freeShipping, expiresAt, providerQuote, integrationSettings));
        } catch (RuntimeException ex) {
            warnings.add("Andreani no pudo cotizar en este momento.");
        }
    }

    private void addCorreoArgentinoQuotes(BookstoreStore store, Long bookstoreId, String postalCode, String itemHash, ShippingPackage pack,
                                           BigDecimal subtotal, BigDecimal surcharge, boolean freeShipping, Instant expiresAt,
                                           List<StorefrontShippingQuoteResponse> results, List<String> warnings) {
        var integration = integrationRepository.findByBookstoreIdAndProvider(bookstoreId, ShippingProviderType.CORREO_ARGENTINO).orElse(null);
        if (integration == null || !Boolean.TRUE.equals(integration.getEnabled())) return;
        var shippingSettings = settingsRepository.findByBookstoreId(bookstoreId).orElse(null);
        if (shippingSettings == null || shippingSettings.getOriginPostalCode() == null || shippingSettings.getOriginPostalCode().isBlank()) return;
        try {
            Map<String, String> credentials = configurationService.requireCredentials(bookstoreId, ShippingProviderType.CORREO_ARGENTINO);
            Map<String, Object> integrationSettings = readSettings(integration.getSettingsJson());
            for (ShippingProviderQuote providerQuote : correoArgentinoClient.quote(shippingSettings.getOriginPostalCode(), postalCode, pack, credentials, integrationSettings)) {
                if (!deliveryTypeEnabled(bookstoreId, ShippingProviderType.CORREO_ARGENTINO, providerQuote.deliveryType())) continue;
                addIfPresent(results, persist(store, postalCode, itemHash, pack, subtotal, surcharge, freeShipping, expiresAt, providerQuote, integrationSettings));
            }
        } catch (RuntimeException ex) {
            warnings.add("Correo Argentino no pudo cotizar en este momento.");
        }
    }

    private void addOcaQuotes(BookstoreStore store, Long bookstoreId, String postalCode, String itemHash, ShippingPackage pack,
                              BigDecimal subtotal, BigDecimal surcharge, boolean freeShipping, Instant expiresAt,
                              List<StorefrontShippingQuoteResponse> results, List<String> warnings) {
        var integration = integrationRepository.findByBookstoreIdAndProvider(bookstoreId, ShippingProviderType.OCA).orElse(null);
        if (integration == null || !Boolean.TRUE.equals(integration.getEnabled())) return;
        var shippingSettings = settingsRepository.findByBookstoreId(bookstoreId).orElse(null);
        if (shippingSettings == null || shippingSettings.getOriginPostalCode() == null || shippingSettings.getOriginPostalCode().isBlank()) return;
        try {
            Map<String, String> credentials = configurationService.requireCredentials(bookstoreId, ShippingProviderType.OCA);
            Map<String, Object> integrationSettings = readSettings(integration.getSettingsJson());
            if (deliveryTypeEnabled(bookstoreId, ShippingProviderType.OCA, ShippingDeliveryType.HOME)) {
                ShippingProviderQuote home = ocaQuoteClient.quoteHome(shippingSettings.getOriginPostalCode(), postalCode, pack, credentials, integrationSettings);
                addIfPresent(results, persist(store, postalCode, itemHash, pack, subtotal, surcharge, freeShipping, expiresAt, home, integrationSettings));
            }
            if (deliveryTypeEnabled(bookstoreId, ShippingProviderType.OCA, ShippingDeliveryType.BRANCH)
                    && (integrationSettings.containsKey("operativaSucursal") || credentials.containsKey("operativaSucursal"))) {
                try {
                    ShippingProviderQuote branch = ocaQuoteClient.quoteBranch(shippingSettings.getOriginPostalCode(), postalCode, pack, credentials, integrationSettings);
                    addIfPresent(results, persist(store, postalCode, itemHash, pack, subtotal, surcharge, freeShipping, expiresAt, branch, integrationSettings));
                } catch (RuntimeException branchError) {
                    log.warn("OCA branch quote failed bookstoreId={} originCp={} destinationCp={} weightGrams={} volumeCm3={} message={}",
                            bookstoreId, shippingSettings.getOriginPostalCode(), postalCode, pack.weightGrams(), pack.volumeCm3(), branchError.getMessage(), branchError);
                    warnings.add(providerWarning("OCA retiro en sucursal", branchError));
                }
            }
        } catch (RuntimeException ex) {
            log.warn("OCA home quote failed bookstoreId={} originCp={} destinationCp={} weightGrams={} dimensions={}x{}x{}cm volumeCm3={} message={}",
                    bookstoreId, shippingSettings.getOriginPostalCode(), postalCode, pack.weightGrams(), pack.widthCm(), pack.heightCm(), pack.depthCm(),
                    pack.volumeCm3(), ex.getMessage(), ex);
            warnings.add(providerWarning("OCA", ex));
        }
    }

    private String providerWarning(String provider, RuntimeException ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) return provider + " no pudo cotizar en este momento.";
        String compact = message.replaceAll("[\r\n\t]+", " ").replaceAll("\\s{2,}", " ").trim();
        if (compact.length() > 300) compact = compact.substring(0, 300) + "…";
        return provider + ": " + compact;
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
            addIfPresent(results, persist(store, postalCode, itemHash, pack, subtotal, ZERO, false, expiresAt, q, Map.of()));
            return;
        }
        for (var service : services) {
            if (!Boolean.TRUE.equals(service.getEnabled())) continue;
            ShippingProviderQuote q = new ShippingProviderQuote(ShippingProviderType.PICKUP_IN_STORE, service.getServiceCode(), service.getDisplayName(),
                    ShippingDeliveryType.STORE_PICKUP, ZERO, null, null, settingText(service.getSettingsJson(), "estimatedDays"), null, service.getSettingsJson());
            addIfPresent(results, persist(store, postalCode, itemHash, pack, subtotal, ZERO, false, expiresAt, q, readSettings(service.getSettingsJson())));
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
            addIfPresent(results, persist(store, postalCode, itemHash, pack, subtotal, surcharge, freeShipping, expiresAt, q, settings));
        }
    }

    private StorefrontShippingQuoteResponse persist(BookstoreStore store, String postalCode, String itemHash, ShippingPackage pack,
                                                    BigDecimal subtotal, BigDecimal surcharge, boolean globalFreeShipping, Instant expiresAt,
                                                    ShippingProviderQuote providerQuote, Map<String, Object> ruleSettings) {
        CarrierRules rules = carrierRules(ruleSettings, postalCode, subtotal, globalFreeShipping);
        if (!rules.eligible()) return null;
        BigDecimal providerAmount = money(providerQuote.amount());
        BigDecimal charged = rules.freeShipping() && providerQuote.deliveryType() != ShippingDeliveryType.STORE_PICKUP
                ? ZERO : money(providerAmount.add(surcharge).add(rules.priceAdjustment()).max(ZERO));
        StoreShippingQuote entity = quoteRepository.save(StoreShippingQuote.builder()
                .id(UUID.randomUUID()).store(store).bookstore(store.getBookstore()).provider(providerQuote.provider())
                .serviceCode(providerQuote.serviceCode()).serviceName(providerQuote.displayName()).deliveryType(providerQuote.deliveryType())
                .destinationPostalCode(postalCode).itemsHash(itemHash).providerAmount(providerAmount).packagingSurcharge(surcharge)
                .chargedAmount(charged).currency("ARS").estimatedDaysMin(providerQuote.estimatedDaysMin()).estimatedDaysMax(providerQuote.estimatedDaysMax())
                .estimatedText(providerQuote.estimatedText()).externalQuoteId(providerQuote.externalQuoteId()).freeShippingApplied(rules.freeShipping())
                .recommended(rules.recommended()).sortPriority(rules.sortPriority())
                .weightGrams(pack.weightGrams()).widthCm(pack.widthCm()).heightCm(pack.heightCm()).depthCm(pack.depthCm()).volumeCm3(pack.volumeCm3())
                .metadataJson(providerQuote.metadataJson()).expiresAt(expiresAt).createdAt(Instant.now()).build());
        return new StorefrontShippingQuoteResponse(entity.getId(), entity.getProvider(), entity.getServiceCode(), entity.getServiceName(), entity.getDeliveryType(),
                entity.getChargedAmount(), entity.getCurrency(), entity.getEstimatedDaysMin(), entity.getEstimatedDaysMax(), entity.getEstimatedText(),
                Boolean.TRUE.equals(entity.getFreeShippingApplied()), Boolean.TRUE.equals(entity.getRecommended()), entity.getSortPriority(), entity.getExpiresAt());
    }

    private CarrierRules carrierRules(Map<String, Object> settings, String postalCode, BigDecimal subtotal, boolean globalFreeShipping) {
        Map<String, Object> safe = settings == null ? Map.of() : settings;
        if (!postalAllowed(postalCode, safe.get("postalCodes"))) return CarrierRules.ineligible();
        if (postalListed(postalCode, safe.get("excludedPostalCodes"))) return CarrierRules.ineligible();
        BigDecimal minOrder = decimal(safe.get("minOrderAmount"), ZERO);
        BigDecimal maxOrder = decimal(safe.get("maxOrderAmount"), null);
        if (subtotal.compareTo(minOrder) < 0 || (maxOrder != null && maxOrder.signum() > 0 && subtotal.compareTo(maxOrder) > 0)) return CarrierRules.ineligible();
        boolean disableGlobalFree = bool(safe.get("disableGlobalFreeShipping"), false);
        BigDecimal ownThreshold = decimal(safe.get("freeShippingThreshold"), null);
        boolean free = ownThreshold != null && ownThreshold.signum() >= 0
                ? subtotal.compareTo(ownThreshold) >= 0
                : globalFreeShipping && !disableGlobalFree;
        BigDecimal adjustment = decimal(safe.get("priceAdjustment"), ZERO);
        boolean recommended = bool(safe.get("recommended"), false);
        int priority = integer(safe.get("sortPriority"), 100);
        return new CarrierRules(true, free, adjustment, recommended, priority);
    }

    private boolean deliveryTypeEnabled(Long bookstoreId, ShippingProviderType provider, ShippingDeliveryType type) {
        List<BookstoreShippingService> matching = shippingServiceRepository
                .findAllByBookstoreIdAndProviderOrderBySortOrderAscIdAsc(bookstoreId, provider).stream()
                .filter(s -> s.getDeliveryType() == type).toList();
        return matching.isEmpty() || matching.stream().anyMatch(s -> Boolean.TRUE.equals(s.getEnabled()));
    }

    private void addIfPresent(List<StorefrontShippingQuoteResponse> results, StorefrontShippingQuoteResponse quote) {
        if (quote != null) results.add(quote);
    }

    private ShippingPackage estimatePackage(List<Long> ids, Map<Long, Integer> quantities, Map<Long, Inventory> inventories, BigDecimal subtotal) {
        BigDecimal booksWeight = ZERO;
        BigDecimal maxWidth = BigDecimal.ZERO;
        BigDecimal maxHeight = BigDecimal.ZERO;
        BigDecimal maxDepth = BigDecimal.ZERO;
        BigDecimal totalBookVolume = BigDecimal.ZERO;
        boolean fallback = false;

        for (Long id : ids) {
            Book book = inventories.get(id).getBook();
            int quantity = quantities.get(id);
            BigDecimal bookWeight = positive(book.getWeightGrams(), DEFAULT_WEIGHT_GRAMS); fallback |= book.getWeightGrams() == null || book.getWeightGrams().signum() <= 0;
            BigDecimal bookWidth = positive(book.getWidthCm(), DEFAULT_WIDTH_CM); fallback |= book.getWidthCm() == null || book.getWidthCm().signum() <= 0;
            BigDecimal bookHeight = positive(book.getHeightCm(), DEFAULT_HEIGHT_CM); fallback |= book.getHeightCm() == null || book.getHeightCm().signum() <= 0;
            BigDecimal bookDepth = positive(book.getDepthCm(), DEFAULT_DEPTH_CM); fallback |= book.getDepthCm() == null || book.getDepthCm().signum() <= 0;

            booksWeight = booksWeight.add(bookWeight.multiply(BigDecimal.valueOf(quantity)));
            maxWidth = maxWidth.max(bookWidth);
            maxHeight = maxHeight.max(bookHeight);
            maxDepth = maxDepth.max(bookDepth);
            totalBookVolume = totalBookVolume.add(bookWidth.multiply(bookHeight).multiply(bookDepth).multiply(BigDecimal.valueOf(quantity)));
        }

        BigDecimal packagingWeight = booksWeight.multiply(new BigDecimal("0.05")).max(new BigDecimal("150"));
        BigDecimal weight = booksWeight.add(packagingWeight);
        BigDecimal width = maxWidth.add(new BigDecimal("2")).max(new BigDecimal("10")).setScale(2, RoundingMode.CEILING);
        BigDecimal height = maxHeight.add(new BigDecimal("2")).max(new BigDecimal("10")).setScale(2, RoundingMode.CEILING);
        BigDecimal footprint = width.multiply(height).multiply(new BigDecimal("0.85"));
        BigDecimal packedDepth = footprint.signum() == 0 ? maxDepth : totalBookVolume.divide(footprint, 2, RoundingMode.CEILING);
        BigDecimal depth = packedDepth.add(new BigDecimal("2")).max(maxDepth.add(new BigDecimal("2"))).max(new BigDecimal("5")).setScale(2, RoundingMode.CEILING);

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
        List<String> values = stringValues(configured);
        if (values.isEmpty()) return true;
        return values.stream().map(this::normalizePostalCode).anyMatch(postalCode::equals);
    }
    private boolean postalListed(String postalCode, Object configured) {
        return stringValues(configured).stream().map(this::normalizePostalCode).anyMatch(postalCode::equals);
    }
    private List<String> stringValues(Object configured) {
        if (configured instanceof Collection<?> values) return values.stream().map(String::valueOf).filter(v -> !v.isBlank()).toList();
        if (configured == null) return List.of();
        return Arrays.stream(String.valueOf(configured).split("[,;\\s]+"))
                .map(String::trim).filter(v -> !v.isBlank()).toList();
    }
    private boolean bool(Object value, boolean fallback) {
        return value == null ? fallback : Boolean.parseBoolean(String.valueOf(value));
    }
    private int integer(Object value, int fallback) {
        if (value == null) return fallback;
        try { return Integer.parseInt(String.valueOf(value)); } catch (NumberFormatException ex) { return fallback; }
    }
    private BigDecimal positive(BigDecimal value, BigDecimal fallback) { return value != null && value.signum() > 0 ? value : fallback; }
    private String normalizePostalCode(String value) { return value == null ? "" : value.replaceAll("\\s+", "").toUpperCase(Locale.ROOT); }
    private BigDecimal money(BigDecimal value) { return value.setScale(2, RoundingMode.HALF_UP); }
    private record CarrierRules(boolean eligible, boolean freeShipping, BigDecimal priceAdjustment, boolean recommended, int sortPriority) {
        static CarrierRules ineligible() { return new CarrierRules(false, false, ZERO, false, 100); }
    }
}
