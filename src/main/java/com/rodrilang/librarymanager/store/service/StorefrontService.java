package com.rodrilang.librarymanager.store.service;

import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.catalog.contribution.service.BookstoreBookEffectiveValues;
import com.rodrilang.librarymanager.catalog.contribution.service.BookstoreBookFieldOverrideService;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPrice;
import com.rodrilang.librarymanager.inventory.pricing.service.InventoryPriceService;
import com.rodrilang.librarymanager.model.*;
import com.rodrilang.librarymanager.store.channel.SalesChannelType;
import com.rodrilang.librarymanager.store.dto.*;
import com.rodrilang.librarymanager.store.model.*;
import com.rodrilang.librarymanager.store.repository.*;
import com.rodrilang.librarymanager.store.order.service.StoreOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.math.BigDecimal;

@Service @RequiredArgsConstructor
public class StorefrontService {
    private final BookstoreStoreRepository storeRepository;
    private final StoreDomainRepository domainRepository;
    private final StorePublicationRepository publicationRepository;
    private final StorefrontProductQueryRepository queryRepository;
    private final InventoryPriceService priceService;
    private final SalesChannelService salesChannelService;
    private final BookstoreBookFieldOverrideService overrideService;
    private final StoreOrderService storeOrderService;

    @Transactional(readOnly = true)
    public StorefrontResponse resolve(String hostOrSlug) {
        BookstoreStore store = resolveActiveStore(hostOrSlug);
        return toStorefront(store);
    }

    @Transactional(readOnly = true)
    public PageResponse<StorefrontProductResponse> products(
            UUID publicStoreId, String q, List<Long> authorIds, List<Long> publisherIds,
            String category, String genre, BigDecimal minPrice, BigDecimal maxPrice,
            Boolean inStock, Boolean featured, String sort, int page, int size
    ) {
        BookstoreStore store = requireActiveStore(publicStoreId);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(100, Math.max(1, size));
        var result = queryRepository.find(store.getId(), q, authorIds, publisherIds, category, genre, minPrice, maxPrice, inStock, featured, sort,
                safePage, safeSize, priceService.today());
        Map<Long, StorePublication> byId = new HashMap<>();
        publicationRepository.findAllByIdIn(result.publicationIds()).forEach(p -> byId.put(p.getId(), p));
        List<StorePublication> ordered = result.publicationIds().stream().map(byId::get).filter(Objects::nonNull).toList();
        List<Long> inventoryIds = ordered.stream().map(p -> p.getInventory().getId()).toList();
        Map<Long, InventoryPrice> prices = priceService.currentFor(inventoryIds);
        Map<Long, Integer> reserved = storeOrderService.activeReservedByInventoryIds(inventoryIds);
        List<StorefrontProductResponse> content = ordered.stream()
                .map(p -> toProduct(store, p, prices.get(p.getInventory().getId()), reserved.getOrDefault(p.getInventory().getId(), 0)))
                .toList();
        return PageResponse.of(new PageImpl<>(content, org.springframework.data.domain.PageRequest.of(safePage, safeSize), result.total()));
    }

    @Transactional(readOnly = true)
    public StorefrontFiltersResponse filters(UUID publicStoreId) {
        BookstoreStore store = requireActiveStore(publicStoreId);
        var data = queryRepository.filters(store.getId(), priceService.today());
        return new StorefrontFiltersResponse(
                data.authors().stream().map(a -> new StorefrontAuthorResponse(a.id(), a.name())).toList(),
                data.publishers().stream().map(p -> new StorefrontPublisherResponse(p.id(), p.name())).toList(),
                data.categories(), data.genres(), data.minPrice(), data.maxPrice()
        );
    }

    @Transactional(readOnly = true)
    public StorefrontProductResponse product(UUID publicStoreId, Long inventoryId) {
        BookstoreStore store = requireActiveStore(publicStoreId);
        StorePublication publication = publicationRepository.findDetailed(store.getId(), inventoryId)
                .filter(p -> Boolean.TRUE.equals(p.getPublished()) && Boolean.TRUE.equals(p.getInventory().getActive()) && Boolean.TRUE.equals(p.getInventory().getBook().getActive()))
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el producto publicado."));
        InventoryPrice price = priceService.current(inventoryId).orElseThrow(() -> new ResourceNotFoundException("El producto no tiene un precio vigente."));
        int reserved = storeOrderService.activeReservedByInventoryIds(List.of(inventoryId)).getOrDefault(inventoryId, 0);
        return toProduct(store, publication, price, reserved);
    }

    private BookstoreStore resolveActiveStore(String hostOrSlug) {
        if (hostOrSlug == null || hostOrSlug.isBlank()) throw new ResourceNotFoundException("No se pudo identificar la tienda.");
        String value = normalizeHost(hostOrSlug);
        BookstoreStore store = domainRepository.findByHostnameIgnoreCaseAndStatus(value, StoreDomainStatus.ACTIVE).map(StoreDomain::getStore)
                .or(() -> storeRepository.findBySlugIgnoreCase(value)).orElseThrow(() -> new ResourceNotFoundException("No se encontró la tienda."));
        assertActive(store);
        return store;
    }
    private BookstoreStore requireActiveStore(UUID publicId) {
        BookstoreStore store = storeRepository.findByPublicId(publicId).orElseThrow(() -> new ResourceNotFoundException("No se encontró la tienda."));
        assertActive(store); return store;
    }
    private void assertActive(BookstoreStore store) {
        if (!Boolean.TRUE.equals(store.getBookstore().getActive()) || !salesChannelService.isEnabled(store.getBookstore().getId(), SalesChannelType.ANAQUEL_STORE)) {
            throw new ResourceNotFoundException("La tienda no está disponible.");
        }
    }
    private StorefrontResponse toStorefront(BookstoreStore s) {
        return new StorefrontResponse(s.getPublicId(), s.getSlug(), s.getDisplayName(), s.getDescription(), s.getLogoUrl(), s.getFaviconUrl(),
                s.getPrimaryColor(), s.getSecondaryColor(), s.getTitleFormat(), s.getShowIsbn(), s.getShowAuthor(), s.getShowPublisher(), s.getShowStock());
    }
    private StorefrontProductResponse toProduct(BookstoreStore store, StorePublication p, InventoryPrice price, int reserved) {
        Inventory i = p.getInventory(); Book b = i.getBook();
        BookstoreBookEffectiveValues effective = overrideService.resolve(b, store.getBookstore().getId());
        String title = textOr(p.getCustomTitle(), effective.title());
        List<StorefrontAuthorResponse> authors = effective.authors().stream().sorted(Comparator.comparing(Author::getName, String.CASE_INSENSITIVE_ORDER))
                .map(a -> new StorefrontAuthorResponse(a.getId(), a.getName())).toList();
        StorefrontPublisherResponse publisher = effective.publisher() == null ? null : new StorefrontPublisherResponse(effective.publisher().getId(), effective.publisher().getName());
        String displayTitle = displayTitle(store.getTitleFormat(), title, authors, publisher);
        int stock = Math.max(0, (i.getStock() == null ? 0 : i.getStock()) - Math.max(0, reserved));
        return new StorefrontProductResponse(i.getId(), b.getId(), slugify(title) + "-" + i.getId(), title, displayTitle,
                Boolean.TRUE.equals(store.getShowIsbn()) ? b.getPreferredIsbn() : null,
                textOr(p.getCustomDescription(), effective.description()),
                Boolean.TRUE.equals(store.getShowAuthor()) ? authors : List.of(),
                Boolean.TRUE.equals(store.getShowPublisher()) ? publisher : null,
                effective.categoryName(), effective.genreName(), effective.coverUrl(), price == null ? null : price.getAmount(),
                stock > 0 ? "IN_STOCK" : "OUT_OF_STOCK", Boolean.TRUE.equals(store.getShowStock()) ? stock : null,
                effective.publicationYear(), Boolean.TRUE.equals(p.getFeatured()), p.getPublishedAt() != null && p.getPublishedAt().isAfter(Instant.now().minus(Duration.ofDays(45))));
    }
    private String displayTitle(StoreTitleFormat format, String title, List<StorefrontAuthorResponse> authors, StorefrontPublisherResponse publisher) {
        if (format == StoreTitleFormat.TITLE_AUTHOR && !authors.isEmpty()) return title + " - " + authors.getFirst().name();
        if (format == StoreTitleFormat.TITLE_PUBLISHER && publisher != null) return title + " - " + publisher.name();
        return title;
    }
    private String normalizeHost(String value) {
        String v = value.trim().toLowerCase(Locale.ROOT);
        int scheme = v.indexOf("://"); if (scheme >= 0) v = v.substring(scheme + 3);
        int slash = v.indexOf('/'); if (slash >= 0) v = v.substring(0, slash);
        int colon = v.indexOf(':'); if (colon >= 0) v = v.substring(0, colon);
        return v;
    }
    private String textOr(String preferred, String fallback) { return preferred == null || preferred.isBlank() ? fallback : preferred; }
    private String slugify(String value) {
        String s = Normalizer.normalize(value == null ? "libro" : value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
        return s.isBlank() ? "libro" : s;
    }
}
