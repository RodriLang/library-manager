package com.rodrilang.librarymanager.store.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.*;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPrice;
import com.rodrilang.librarymanager.inventory.pricing.service.InventoryPriceService;
import com.rodrilang.librarymanager.model.*;
import com.rodrilang.librarymanager.repository.*;
import com.rodrilang.librarymanager.store.channel.SalesChannelType;
import com.rodrilang.librarymanager.store.dto.*;
import com.rodrilang.librarymanager.store.model.*;
import com.rodrilang.librarymanager.store.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Pattern;

@Service @RequiredArgsConstructor
public class StoreAdminService {
    private static final Pattern NON_SLUG = Pattern.compile("[^a-z0-9]+");
    private final BookstoreContext bookstoreContext;
    private final BookstoreRepository bookstoreRepository;
    private final InventoryRepository inventoryRepository;
    private final BookstoreStoreRepository storeRepository;
    private final StoreDomainRepository domainRepository;
    private final StorePublicationRepository publicationRepository;
    private final InventoryPriceService priceService;
    private final SalesChannelService salesChannelService;

    @Value("${app.storefront.base-domain:anaquel.com.ar}") private String baseDomain;

    @Transactional
    public StoreSettingsResponse getOrCreateSettings() {
        return toSettings(ensureCurrentStore());
    }

    @Transactional
    public StoreSettingsResponse updateSettings(StoreSettingsRequest request) {
        BookstoreStore store = ensureCurrentStore();
        store.setDisplayName(request.displayName().trim());
        store.setTitleFormat(request.titleFormat());
        store.setShowIsbn(request.showIsbn());
        store.setShowAuthor(request.showAuthor());
        store.setShowPublisher(request.showPublisher());
        store.setShowStock(request.showStock());
        store.setLogoUrl(blankToNull(request.logoUrl()));
        store.setFaviconUrl(blankToNull(request.faviconUrl()));
        store.setPrimaryColor(blankToNull(request.primaryColor()));
        store.setSecondaryColor(blankToNull(request.secondaryColor()));
        if (request.phone() != null) {
            store.getBookstore().setPhone(blankToNull(request.phone()));
            bookstoreRepository.save(store.getBookstore());
        }
        store.setDescription(blankToNull(request.description()));
        return toSettings(storeRepository.save(store));
    }


    @Transactional
    public StoreDomainResponse addCustomDomain(String rawHostname) {
        BookstoreStore store = ensureCurrentStore();
        String hostname = normalizeHostname(rawHostname);
        if (hostname.isBlank() || !hostname.contains(".")) {
            throw new BusinessException("Ingresá un dominio válido.");
        }
        StoreDomain existing = domainRepository.findByHostnameIgnoreCase(hostname).orElse(null);
        if (existing != null && !existing.getStore().getId().equals(store.getId())) {
            throw new BusinessException("Ese dominio ya está siendo utilizado por otra tienda.");
        }
        StoreDomain domain = existing != null ? existing : StoreDomain.builder()
                        .store(store)
                        .hostname(hostname)
                        .type(StoreDomainType.CUSTOM)
                        .status(StoreDomainStatus.PENDING)
                        .verificationToken(UUID.randomUUID().toString().replace("-", ""))
                        .build();
        if (domain.getVerificationToken() == null || domain.getVerificationToken().isBlank()) {
            domain.setVerificationToken(UUID.randomUUID().toString().replace("-", ""));
        }
        domain = domainRepository.save(domain);
        return toDomain(domain);
    }

    @Transactional
    public void removeCustomDomain(Long domainId) {
        BookstoreStore store = ensureCurrentStore();
        StoreDomain domain = domainRepository.findById(domainId)
                .filter(d -> d.getStore().getId().equals(store.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el dominio."));
        if (domain.getType() == StoreDomainType.ANAQUEL_SUBDOMAIN) {
            throw new BusinessException("El subdominio de Anaquel no se puede eliminar.");
        }
        domainRepository.delete(domain);
    }

    @Transactional(readOnly = true)
    public Page<StoreProductAdminResponse> products(StoreProductAdminFilters filters, Pageable pageable) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        BookstoreStore store = storeRepository.findByBookstoreId(bookstoreId).orElse(null);
        String query = filters.normalizedQuery();

        Page<Inventory> page = inventoryRepository.findAll((root, cq, cb) -> {
            var book = root.join("book");
            var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
            predicates.add(cb.equal(root.get("bookstore").get("id"), bookstoreId));
            predicates.add(cb.isTrue(root.<Boolean>get("active")));
            predicates.add(cb.isTrue(book.<Boolean>get("active")));

            if (!query.isBlank()) {
                String like = "%" + query + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(book.<String>get("title")), like),
                        cb.like(cb.lower(book.<String>get("isbn13")), like),
                        cb.like(cb.lower(book.<String>get("isbn10")), like)
                ));
            }

            switch (filters.stock()) {
                case AVAILABLE -> predicates.add(cb.greaterThan(root.<Integer>get("stock"), root.<Integer>get("minimumStock")));
                case LOW -> predicates.add(cb.and(
                        cb.greaterThan(root.<Integer>get("stock"), 0),
                        cb.lessThanOrEqualTo(root.<Integer>get("stock"), root.<Integer>get("minimumStock"))
                ));
                case OUT -> predicates.add(cb.equal(root.<Integer>get("stock"), 0));
                case ALL -> { }
            }

            if (filters.condition() != null) {
                predicates.add(cb.equal(root.get("condition"), filters.condition()));
            }
            if (!filters.publisherIds().isEmpty()) {
                predicates.add(book.get("publisher").get("id").in(filters.publisherIds()));
            }
            if (!filters.authorIds().isEmpty()) {
                var authorSub = cq.subquery(Long.class);
                var authorBook = authorSub.from(Book.class);
                var authorJoin = authorBook.join("authors");
                authorSub.select(authorBook.get("id").as(Long.class));
                authorSub.where(
                        cb.equal(authorBook.get("id"), book.get("id")),
                        authorJoin.get("id").in(filters.authorIds())
                );
                predicates.add(cb.exists(authorSub));
            }
            if (Boolean.TRUE.equals(filters.consignment())) {
                predicates.add(cb.greaterThan(root.<Integer>get("consignmentStock"), 0));
            }

            if (filters.priceMode() != com.rodrilang.librarymanager.enums.InventoryPriceMode.ALL) {
                var priceSub = cq.subquery(Long.class);
                var price = priceSub.from(InventoryPrice.class);
                priceSub.select(price.get("id").as(Long.class));
                priceSub.where(
                        cb.equal(price.get("inventory").get("id"), root.get("id")),
                        cb.lessThanOrEqualTo(price.<LocalDate>get("effectiveFrom"), LocalDate.now(ZoneId.of("America/Argentina/Buenos_Aires")))
                );
                var hasCurrentPrice = cb.exists(priceSub);
                predicates.add(filters.priceMode() == com.rodrilang.librarymanager.enums.InventoryPriceMode.WITH_PRICE
                        ? hasCurrentPrice
                        : cb.not(hasCurrentPrice));
            }

            if (store == null) {
                if (Boolean.TRUE.equals(filters.published()) || Boolean.TRUE.equals(filters.featured())) {
                    predicates.add(cb.disjunction());
                }
            } else {
                if (filters.published() != null) {
                    var publishedSub = cq.subquery(Long.class);
                    var publishedPub = publishedSub.from(StorePublication.class);
                    publishedSub.select(publishedPub.get("id").as(Long.class));
                    publishedSub.where(
                            cb.equal(publishedPub.get("store").get("id"), store.getId()),
                            cb.equal(publishedPub.get("inventory").get("id"), root.get("id")),
                            cb.isTrue(publishedPub.<Boolean>get("published"))
                    );
                    var isPublished = cb.exists(publishedSub);
                    predicates.add(filters.published() ? isPublished : cb.not(isPublished));
                }

                if (filters.featured() != null) {
                    var featuredSub = cq.subquery(Long.class);
                    var featuredPub = featuredSub.from(StorePublication.class);
                    featuredSub.select(featuredPub.get("id").as(Long.class));
                    featuredSub.where(
                            cb.equal(featuredPub.get("store").get("id"), store.getId()),
                            cb.equal(featuredPub.get("inventory").get("id"), root.get("id")),
                            cb.isTrue(featuredPub.<Boolean>get("featured"))
                    );
                    var isFeatured = cb.exists(featuredSub);
                    predicates.add(filters.featured() ? isFeatured : cb.not(isFeatured));
                }
            }

            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        }, pageable);

        if (store == null) return page.map(i -> toAdminProduct(i, null, null));

        List<Long> inventoryIds = page.getContent().stream().map(Inventory::getId).toList();
        Map<Long, InventoryPrice> prices = priceService.currentFor(inventoryIds);
        Map<Long, StorePublication> pubs = publicationRepository
                .findAllByStoreIdAndInventoryIdIn(store.getId(), inventoryIds)
                .stream()
                .collect(java.util.stream.Collectors.toMap(p -> p.getInventory().getId(), p -> p));
        return page.map(i -> toAdminProduct(i, pubs.get(i.getId()), prices.get(i.getId())));
    }

    @Transactional
    public StoreProductAdminResponse updatePublication(Long inventoryId, UpdateStorePublicationRequest request) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        BookstoreStore store = ensureCurrentStore();
        Inventory inventory = inventoryRepository.findByIdAndBookstoreId(inventoryId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el libro en el inventario de esta librería."));
        StorePublication publication = publicationRepository.findByStoreIdAndInventoryId(store.getId(), inventoryId)
                .orElseGet(() -> StorePublication.builder().store(store).inventory(inventory).build());

        applyPublicationUpdate(inventory, publication, request.published(), request.featured(), request.featuredOrder());
        publication.setCustomTitle(blankToNull(request.customTitle()));
        publication.setCustomDescription(blankToNull(request.customDescription()));
        publication.setSeoTitle(blankToNull(request.seoTitle()));
        publication.setSeoDescription(blankToNull(request.seoDescription()));
        publication = publicationRepository.save(publication);
        return toAdminProduct(inventory, publication, priceService.current(inventoryId).orElse(null));
    }

    @Transactional
    public StorePublicationBulkUpdateResponse bulkUpdatePublications(BulkUpdateStorePublicationsRequest request) {
        if (request.published() == null && request.featured() == null) {
            throw new BusinessException("Indicá si querés publicar, ocultar, destacar o quitar de destacados.");
        }

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        BookstoreStore store = ensureCurrentStore();
        List<Long> requestedIds = request.inventoryIds();
        List<Inventory> inventories = inventoryRepository.findAllById(requestedIds).stream()
                .filter(i -> i.getBookstore() != null && Objects.equals(i.getBookstore().getId(), bookstoreId))
                .toList();
        Map<Long, Inventory> inventoryById = inventories.stream()
                .collect(java.util.stream.Collectors.toMap(Inventory::getId, i -> i));
        Map<Long, InventoryPrice> currentPrices = priceService.currentFor(requestedIds);
        Map<Long, StorePublication> publicationByInventoryId = publicationRepository
                .findAllByStoreIdAndInventoryIdIn(store.getId(), requestedIds)
                .stream()
                .collect(java.util.stream.Collectors.toMap(p -> p.getInventory().getId(), p -> p));

        List<StorePublication> toSave = new ArrayList<>();
        List<StorePublicationBulkErrorResponse> errors = new ArrayList<>();

        for (Long inventoryId : requestedIds) {
            Inventory inventory = inventoryById.get(inventoryId);
            if (inventory == null) {
                errors.add(new StorePublicationBulkErrorResponse(inventoryId, "No se encontró el libro en el inventario de esta librería."));
                continue;
            }

            StorePublication publication = publicationByInventoryId.get(inventoryId);
            if (publication == null) {
                publication = StorePublication.builder().store(store).inventory(inventory).build();
            }

            Boolean resultingPublished = request.published() != null ? request.published() : publication.getPublished();
            Boolean resultingFeatured = request.featured() != null ? request.featured() : publication.getFeatured();

            if (Boolean.TRUE.equals(resultingPublished)) {
                if (!Boolean.TRUE.equals(inventory.getActive()) || !Boolean.TRUE.equals(inventory.getBook().getActive())) {
                    errors.add(new StorePublicationBulkErrorResponse(inventoryId, "El libro debe estar activo para publicarlo."));
                    continue;
                }
                if (!currentPrices.containsKey(inventoryId)) {
                    errors.add(new StorePublicationBulkErrorResponse(inventoryId, "El libro necesita un precio vigente antes de publicarse."));
                    continue;
                }
            }
            if (Boolean.TRUE.equals(resultingFeatured) && !Boolean.TRUE.equals(resultingPublished)) {
                errors.add(new StorePublicationBulkErrorResponse(inventoryId, "El libro debe estar publicado para destacarlo."));
                continue;
            }

            applyPublicationUpdateWithoutValidation(publication, request.published(), request.featured(), null);
            toSave.add(publication);
        }

        if (!toSave.isEmpty()) publicationRepository.saveAll(toSave);
        return new StorePublicationBulkUpdateResponse(
                requestedIds.size(),
                toSave.size(),
                errors.size(),
                List.copyOf(errors)
        );
    }

    private void applyPublicationUpdate(
            Inventory inventory,
            StorePublication publication,
            Boolean published,
            Boolean featured,
            Integer featuredOrder
    ) {
        Boolean resultingPublished = published != null ? published : publication.getPublished();
        Boolean resultingFeatured = featured != null ? featured : publication.getFeatured();

        if (Boolean.TRUE.equals(resultingPublished)) {
            if (!Boolean.TRUE.equals(inventory.getActive()) || !Boolean.TRUE.equals(inventory.getBook().getActive())) {
                throw new BusinessException("El libro debe estar activo para publicarlo.");
            }
            if (priceService.current(inventory.getId()).isEmpty()) {
                throw new BusinessException("El libro necesita un precio vigente antes de publicarse.");
            }
        }
        if (Boolean.TRUE.equals(resultingFeatured) && !Boolean.TRUE.equals(resultingPublished)) {
            throw new BusinessException("El libro debe estar publicado para destacarlo.");
        }

        applyPublicationUpdateWithoutValidation(publication, published, featured, featuredOrder);
    }

    private void applyPublicationUpdateWithoutValidation(
            StorePublication publication,
            Boolean published,
            Boolean featured,
            Integer featuredOrder
    ) {
        if (published != null) {
            if (published && !Boolean.TRUE.equals(publication.getPublished())) {
                publication.setPublishedAt(Instant.now());
            }
            publication.setPublished(published);
            if (!published) {
                publication.setFeatured(false);
                publication.setFeaturedOrder(null);
            }
        }
        if (featured != null) {
            publication.setFeatured(featured);
            if (!featured) publication.setFeaturedOrder(null);
        }
        if (featuredOrder != null) publication.setFeaturedOrder(featuredOrder);
    }

    private BookstoreStore ensureCurrentStore() {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        return storeRepository.findByBookstoreId(bookstoreId).orElseGet(() -> createStore(bookstoreId));
    }

    private BookstoreStore createStore(Long bookstoreId) {
        Bookstore bookstore = bookstoreRepository.findById(bookstoreId).orElseThrow(() -> new ResourceNotFoundException("No se encontró la librería."));
        String base = slugify(bookstore.getName());
        String slug = uniqueSlug(base.isBlank() ? "libreria-" + bookstoreId : base);
        BookstoreStore store = storeRepository.save(BookstoreStore.builder()
                .publicId(UUID.randomUUID()).bookstore(bookstore).slug(slug).displayName(bookstore.getName()).build());
        domainRepository.save(StoreDomain.builder().store(store).hostname(slug + "." + baseDomain.toLowerCase(Locale.ROOT))
                .type(StoreDomainType.ANAQUEL_SUBDOMAIN).status(StoreDomainStatus.ACTIVE).verifiedAt(Instant.now()).build());
        return store;
    }

    private String uniqueSlug(String base) {
        String candidate = base; int suffix = 2;
        while (storeRepository.existsBySlugIgnoreCase(candidate)) candidate = base + "-" + suffix++;
        return candidate;
    }
    private String slugify(String value) {
        String normalized = Normalizer.normalize(value == null ? "" : value, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
        return NON_SLUG.matcher(normalized).replaceAll("-").replaceAll("^-|-$", "");
    }
    private StoreSettingsResponse toSettings(BookstoreStore store) {
        boolean enabled = salesChannelService.isEnabled(store.getBookstore().getId(), SalesChannelType.ANAQUEL_STORE);
        List<StoreDomainResponse> domains = domainRepository.findAllByStoreIdOrderByIdAsc(store.getId()).stream()
                .map(d -> toDomain(d)).toList();
        return new StoreSettingsResponse(store.getPublicId(), store.getSlug(), store.getDisplayName(), enabled, store.getTitleFormat(),
                store.getShowIsbn(), store.getShowAuthor(), store.getShowPublisher(), store.getShowStock(), store.getLogoUrl(), store.getFaviconUrl(),
                store.getPrimaryColor(), store.getSecondaryColor(), store.getBookstore().getPhone(), store.getDescription(), domains);
    }
    private StoreDomainResponse toDomain(StoreDomain d) {
        return new StoreDomainResponse(d.getId(), d.getHostname(), d.getType(), d.getStatus(), d.getVerificationToken());
    }
    private String normalizeHostname(String value) {
        if (value == null) return "";
        String v = value.trim().toLowerCase(Locale.ROOT);
        int scheme = v.indexOf("://"); if (scheme >= 0) v = v.substring(scheme + 3);
        int slash = v.indexOf('/'); if (slash >= 0) v = v.substring(0, slash);
        int colon = v.indexOf(':'); if (colon >= 0) v = v.substring(0, colon);
        return v.replaceAll("\\.$", "");
    }
    private StoreProductAdminResponse toAdminProduct(Inventory i, StorePublication p, InventoryPrice price) {
        Book b = i.getBook();
        return new StoreProductAdminResponse(i.getId(), b.getId(), b.getTitle(), b.getPreferredIsbn(), b.getCoverUrl(), i.getStock(), price == null ? null : price.getAmount(),
                p != null && Boolean.TRUE.equals(p.getPublished()), p != null && Boolean.TRUE.equals(p.getFeatured()), p == null ? null : p.getFeaturedOrder(),
                p == null ? null : p.getCustomTitle(), p == null ? null : p.getCustomDescription());
    }
    private String blankToNull(String s) { return s == null || s.isBlank() ? null : s.trim(); }
}
