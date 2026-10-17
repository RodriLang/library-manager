package com.rodrilang.librarymanager.inventory.pricing.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.dto.response.PageResponse;
import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.importer.price.configuration.enums.HeaderStrategy;
import com.rodrilang.librarymanager.importer.price.configuration.enums.PriceListField;
import com.rodrilang.librarymanager.importer.price.configuration.enums.PriceListValueType;
import com.rodrilang.librarymanager.importer.price.configuration.enums.SheetStrategy;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListColumnMapping;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListImportConfig;
import com.rodrilang.librarymanager.importer.price.configuration.parser.StreamingConfigurablePriceListParser;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import com.rodrilang.librarymanager.importer.price.storage.PriceListImportFileStorage;
import com.rodrilang.librarymanager.inventory.pricing.dto.*;
import com.rodrilang.librarymanager.inventory.pricing.event.InventoryPriceImportPreviewRequestedEvent;
import com.rodrilang.librarymanager.inventory.pricing.model.*;
import com.rodrilang.librarymanager.inventory.pricing.repository.InventoryPriceImportItemRepository;
import com.rodrilang.librarymanager.inventory.pricing.repository.InventoryPriceImportRepository;
import com.rodrilang.librarymanager.inventory.pricing.repository.InventoryPriceImportProviderRowJdbcRepository;
import com.rodrilang.librarymanager.inventory.pricing.job.dto.InventoryPriceImportJobResponse;
import com.rodrilang.librarymanager.inventory.pricing.job.model.InventoryPriceImportJobStatus;
import com.rodrilang.librarymanager.inventory.pricing.job.service.InventoryPriceImportJobService;
import com.rodrilang.librarymanager.inventory.pricing.repository.InventoryPriceRepository;
import com.rodrilang.librarymanager.inventory.pricing.storage.NormalizedPriceListStorage;
import com.rodrilang.librarymanager.isbn.service.CanonicalIsbnResolver;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.BookstoreRepository;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryPriceImportService {

    private static final BigDecimal LARGE_CHANGE_PERCENT = BigDecimal.valueOf(50);

    private final BookstoreContext bookstoreContext;
    private final BookstoreRepository bookstoreRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryPriceRepository priceRepository;
    private final InventoryPriceService priceService;
    private final BookstorePriceListFormatService formatService;
    private final InventoryPriceImportRepository importRepository;
    private final InventoryPriceImportItemRepository itemRepository;
    private final PriceListImportFileStorage fileStorage;
    private final StreamingConfigurablePriceListParser parser;
    private final NormalizedPriceListStorage normalizedStorage;
    private final CanonicalIsbnResolver canonicalIsbnResolver;
    private final InventoryPriceImportProviderRowJdbcRepository providerRowJdbcRepository;
    private final InventoryPriceImportJobService jobService;
    private final ApplicationEventPublisher eventPublisher;

    @Transactional
    public InventoryPriceImportPreviewStartResponse startPreview(
            Long formatId,
            String sourceName,
            LocalDate effectiveFrom,
            MultipartFile file
    ) {
        validateEffectiveFrom(effectiveFrom);

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        Bookstore bookstore = bookstoreRepository.findById(bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la librería seleccionada."));
        BookstorePriceListFormat format = formatService.getForCurrentBookstore(formatId);

        Path source = fileStorage.store(file);
        try {
            Instant startedAt = Instant.now();
            InventoryPriceImport priceImport = importRepository.save(InventoryPriceImport.builder()
                    .bookstore(bookstore)
                    .format(format)
                    .provider(format.getProvider())
                    .sourceName(resolveSourceName(sourceName, format))
                    .originalFilename(resolveFilename(file))
                    .effectiveFrom(effectiveFrom)
                    .status(InventoryPriceImportStatus.PREVIEW_PROCESSING)
                    .createdByUserId(bookstoreContext.getCurrentUserId())
                    .processingStartedAt(startedAt)
                    .processingFinishedAt(null)
                    .processingError(null)
                    .build());

            eventPublisher.publishEvent(new InventoryPriceImportPreviewRequestedEvent(
                    priceImport.getId(),
                    source.toString()
            ));

            return new InventoryPriceImportPreviewStartResponse(
                    priceImport.getId(),
                    InventoryPriceImportStatus.PREVIEW_PROCESSING,
                    startedAt
            );
        } catch (RuntimeException exception) {
            fileStorage.deleteQuietly(source);
            throw exception;
        }
    }

    @Transactional
    public void processPreview(Long importId, Path source) {
        long totalStarted = System.nanoTime();
        InventoryPriceImport priceImport = importRepository.findById(importId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la importación solicitada."));

        if (priceImport.getStatus() != InventoryPriceImportStatus.PREVIEW_PROCESSING) {
            log.info(
                    "Skipping price import preview importId={} because status={}",
                    importId,
                    priceImport.getStatus()
            );
            return;
        }

        Long bookstoreId = priceImport.getBookstore().getId();
        BookstorePriceListFormat format = priceImport.getFormat();
        if (format == null) {
            throw new BusinessException("La importación no tiene un formato de lista asociado.");
        }

        log.info("PRICE IMPORT PREVIEW START importId={} bookstoreId={} formatId={} providerId={} effectiveFrom={} fileBytes={}",
                importId, bookstoreId, format.getId(),
                format.getProvider() == null ? null : format.getProvider().getId(),
                priceImport.getEffectiveFrom(), safePreviewFileSize(source));

        long phaseStarted = System.nanoTime();
        List<Inventory> inventories = inventoryRepository.findAllByBookstoreIdAndActiveTrue(bookstoreId);
        logPreviewPhase(importId, "load-inventory", phaseStarted, "inventories=" + inventories.size());

        phaseStarted = System.nanoTime();
        MatchIndex matchIndex = buildMatchIndex(inventories);
        logPreviewPhase(importId, "build-match-index", phaseStarted, "inventories=" + inventories.size());

        LocalDate applicationDate = priceService.importApplicationDate(priceImport.getEffectiveFrom());

        phaseStarted = System.nanoTime();
        Map<Long, InventoryPrice> currentPrices = currentPricesAt(bookstoreId, applicationDate);
        logPreviewPhase(importId, "load-current-prices", phaseStarted, "prices=" + currentPrices.size());

        phaseStarted = System.nanoTime();
        Map<Long, InventoryPrice> scheduledPrices = scheduledPricesAt(bookstoreId, applicationDate);
        logPreviewPhase(importId, "load-scheduled-prices", phaseStarted, "prices=" + scheduledPrices.size());

        Map<String, List<InventoryPriceImportItem>> itemsByDuplicateKey = new HashMap<>();
        List<InventoryPriceImportItem> items = new ArrayList<>();
        List<InventoryPriceImportProviderRowJdbcRepository.StagedRow> providerRows = new ArrayList<>();

        AtomicInteger total = new AtomicInteger();
        AtomicInteger matched = new AtomicInteger();
        AtomicInteger unmatched = new AtomicInteger();

        Path normalizedFile = null;
        try {
            normalizedFile = Files.createTempFile("anaquel-price-list-", ".csv");
            phaseStarted = System.nanoTime();
            try (BufferedWriter writer = Files.newBufferedWriter(normalizedFile, StandardCharsets.UTF_8)) {
                writer.write("isbn,title,author,publisher,price");
                writer.newLine();

                PriceListImportConfig parserConfig = toParserConfig(format);
                parser.parse(source, parserConfig, row -> {
                    if (isCompletelyEmpty(row)) {
                        return;
                    }
                    total.incrementAndGet();
                    writeNormalized(writer, row);

                    MatchResult match = match(row, matchIndex);
                    Long matchedBookId = match.inventory() != null ? match.inventory().getBook().getId() : null;
                    if (format.getProvider() != null) {
                        providerRows.add(toStagedProviderRow(row, matchedBookId));
                    }
                    if (match.inventory() == null) {
                        if (match.ambiguous()) {
                            InventoryPriceImportItem item = buildAmbiguousItem(priceImport, row, match.reason());
                            items.add(item);
                        } else {
                            unmatched.incrementAndGet();
                        }
                        return;
                    }

                    matched.incrementAndGet();
                    Inventory inventory = match.inventory();
                    InventoryPriceImportItem item = classify(
                            priceImport,
                            inventory,
                            row,
                            applicationDate,
                            currentPrices.get(inventory.getId()),
                            scheduledPrices.get(inventory.getId())
                    );

                    String duplicateKey = duplicateKey(row, inventory);
                    List<InventoryPriceImportItem> existingItems =
                            itemsByDuplicateKey.computeIfAbsent(duplicateKey, ignored -> new ArrayList<>());

                    boolean samePriceAlreadyPresent = existingItems.stream()
                            .anyMatch(existing -> samePrice(
                                    existing.getIncomingPrice(),
                                    item.getIncomingPrice()
                            ));

                    if (samePriceAlreadyPresent) {
                        return;
                    }

                    if (!existingItems.isEmpty()) {
                        boolean duplicateByIsbn = hasIsbn(row.isbn());
                        for (InventoryPriceImportItem existing : existingItems) {
                            markDuplicateConflict(existing, duplicateByIsbn);
                        }
                        markDuplicateConflict(item, duplicateByIsbn);
                    }

                    existingItems.add(item);
                    items.add(item);
                });
            }
            logPreviewPhase(
                    importId,
                    "parse-match-classify",
                    phaseStarted,
                    "rows=" + total.get() + " matched=" + matched.get() + " items=" + items.size()
            );

            log.info("PRICE IMPORT PREVIEW PARSED importId={} rows={} matches={} providerRows={} elapsedMs={}",
                    importId, total.get(), matched.get(), providerRows.size(), elapsedMillis(totalStarted));
            if (!providerRows.isEmpty()) {
                phaseStarted = System.nanoTime();
                log.info("PRICE IMPORT PREVIEW PROVIDER INSERT START importId={} rows={}", importId, providerRows.size());
                providerRowJdbcRepository.insertAll(importId, providerRows);
                logPreviewPhase(
                        importId,
                        "persist-provider-rows",
                        phaseStarted,
                        "providerRows=" + providerRows.size()
                );
            }

            phaseStarted = System.nanoTime();
            log.info("PRICE IMPORT PREVIEW ITEMS SAVE START importId={} items={}", importId, items.size());
            itemRepository.saveAll(items);
            updateCounters(priceImport, total.get(), matched.get(), unmatched.get(), items);
            logPreviewPhase(
                    importId,
                    "persist-preview",
                    phaseStarted,
                    "items=" + items.size() + " providerRows=" + providerRows.size()
            );

            phaseStarted = System.nanoTime();
            log.info("PRICE IMPORT PREVIEW UPLOAD START importId={} bytes={}",
                    importId, safePreviewFileSize(normalizedFile));
            try {
                NormalizedPriceListStorage.StoredRawFile stored = normalizedStorage.upload(bookstoreId, normalizedFile);
                priceImport.setNormalizedFilePublicId(stored.publicId());
                priceImport.setNormalizedFileUrl(stored.secureUrl());
            } catch (RuntimeException storageException) {
                log.warn("No se pudo guardar la copia normalizada de la lista {}", priceImport.getId(), storageException);
            }
            logPreviewPhase(importId, "upload-normalized", phaseStarted, "");

            priceImport.setStatus(InventoryPriceImportStatus.PREVIEW_READY);
            priceImport.setProcessingFinishedAt(Instant.now());
            priceImport.setProcessingError(null);
            if (format.getProvider() != null && !providerRows.isEmpty()) {
                log.info("PRICE IMPORT PREVIEW CATALOG JOB START importId={} providerRows={}", importId, providerRows.size());
                jobService.ensureProviderCatalogSync(importId, providerRows.size());
                log.info("PRICE IMPORT PREVIEW CATALOG JOB READY importId={}", importId);
            }

            log.info(
                    "PRICE IMPORT PREVIEW COMPLETED importId={} totalMs={} rows={} matched={} unmatched={} items={}",
                    importId,
                    elapsedMillis(totalStarted),
                    total.get(),
                    matched.get(),
                    unmatched.get(),
                    items.size()
            );
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new BusinessException("No se pudo procesar la lista de precios: " + safeMessage(exception));
        } finally {
            if (normalizedFile != null) {
                try {
                    Files.deleteIfExists(normalizedFile);
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static long safePreviewFileSize(Path file) {
        try {
            return file == null ? -1L : Files.size(file);
        } catch (java.io.IOException ignored) {
            return -1L;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markPreviewFailed(Long importId, Throwable exception) {
        InventoryPriceImport priceImport = importRepository.findById(importId).orElse(null);
        if (priceImport == null || priceImport.getStatus() != InventoryPriceImportStatus.PREVIEW_PROCESSING) {
            return;
        }

        priceImport.setStatus(InventoryPriceImportStatus.FAILED);
        priceImport.setProcessingFinishedAt(Instant.now());
        priceImport.setProcessingError(errorMessage(exception));
        providerRowJdbcRepository.deleteByImportId(importId);
        log.warn("PRICE IMPORT PREVIEW FAILED importId={} error={}", importId, priceImport.getProcessingError());
    }

    @Transactional(readOnly = true)
    public void assertAccessible(Long importId) {
        getImport(importId, bookstoreContext.getCurrentBookstoreId());
    }

    @Transactional(readOnly = true)
    public InventoryPriceImportPreviewResponse get(Long importId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        InventoryPriceImport priceImport = getImport(importId, bookstoreId);
        if (priceImport.getStatus() == InventoryPriceImportStatus.PREVIEW_PROCESSING) {
            throw new BusinessException("La previsualización todavía se está generando.");
        }
        return toPreview(priceImport, itemRepository.findAllByPriceImportIdOrderByRowNumberAsc(importId));
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryPriceImportHistoryResponse> history(Pageable pageable) {
        return history(null, pageable);
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryPriceImportHistoryResponse> history(Long providerId, Pageable pageable) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        Page<InventoryPriceImport> imports = providerId == null
                ? importRepository.findAllByBookstoreIdOrderByCreatedAtDesc(bookstoreId, pageable)
                : importRepository.findAllByBookstoreIdAndProviderIdOrderByCreatedAtDesc(bookstoreId, providerId, pageable);
        return PageResponse.of(imports.map(this::toHistory));
    }

    @Transactional(readOnly = true)
    public List<InventoryPriceImportActivityResponse> active(Long providerId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        List<InventoryPriceImport> imports = importRepository.findActiveByBookstoreId(
                bookstoreId,
                EnumSet.of(
                        InventoryPriceImportStatus.PREVIEW_PROCESSING,
                        InventoryPriceImportStatus.PREVIEW_READY,
                        InventoryPriceImportStatus.PROCESSING
                ),
                EnumSet.of(
                        InventoryPriceImportJobStatus.PENDING,
                        InventoryPriceImportJobStatus.PROCESSING,
                        InventoryPriceImportJobStatus.FAILED
                )
        );

        if (providerId != null) {
            imports = imports.stream()
                    .filter(priceImport -> priceImport.getProvider() != null
                            && Objects.equals(priceImport.getProvider().getId(), providerId))
                    .toList();
        }

        List<Long> importIds = imports.stream().map(InventoryPriceImport::getId).toList();
        Map<Long, List<InventoryPriceImportJobResponse>> jobsByImport = jobService.listByImportIds(importIds);

        return imports.stream()
                .map(priceImport -> toActivity(
                        priceImport,
                        jobsByImport.getOrDefault(priceImport.getId(), List.of())
                ))
                .toList();
    }

    @Transactional
    public void cancel(Long importId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        InventoryPriceImport priceImport = getImport(importId, bookstoreId);
        if (priceImport.getStatus() != InventoryPriceImportStatus.PREVIEW_READY) {
            throw new BusinessException("Sólo se puede cancelar una importación que todavía no fue aplicada.");
        }
        priceImport.setStatus(InventoryPriceImportStatus.CANCELLED);
    }

    @Transactional
    public InventoryPriceImportPreviewResponse resolveDuplicate(
            Long importId,
            Long inventoryId,
            ResolveInventoryPriceImportDuplicateRequest request
    ) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        InventoryPriceImport priceImport = getImport(importId, bookstoreId);

        if (priceImport.getStatus() != InventoryPriceImportStatus.PREVIEW_READY) {
            throw new BusinessException("Esta importación ya no se encuentra pendiente de aplicación.");
        }

        List<InventoryPriceImportItem> group =
                itemRepository
                        .findAllByPriceImportIdAndInventoryIdOrderByRowNumberAsc(importId, inventoryId)
                        .stream()
                        .filter(InventoryPriceImportItem::isDuplicateGroup)
                        .toList();

        if (group.size() < 2) {
            throw new BusinessException("No se encontró un conflicto de precios duplicados para este libro.");
        }

        InventoryPriceImportItem selected = group.stream()
                .filter(item -> Objects.equals(item.getId(), request.selectedItemId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException("El precio seleccionado no pertenece a este conflicto."));

        for (InventoryPriceImportItem item : group) {
            if (Objects.equals(item.getId(), selected.getId())) {
                ClassificationResult result =
                        classifyPrice(
                                item.getIncomingPrice(),
                                item.getCurrentPrice(),
                                item.getExistingScheduledPrice()
                        );

                item.setClassification(result.classification());
                item.setConflictReason(result.reason());
                item.setSelectedDefault(result.selectedDefault());
                item.setChangePercent(result.changePercent());
                item.setDiscarded(false);
            } else {
                item.setClassification(InventoryPriceImportClassification.DUPLICATE_CONFLICT);
                item.setConflictReason("Versión descartada al resolver el precio duplicado.");
                item.setSelectedDefault(false);
                item.setDiscarded(true);
            }
        }

        itemRepository.saveAll(group);

        List<InventoryPriceImportItem> allItems = itemRepository.findAllByPriceImportIdOrderByRowNumberAsc(importId);

        updateCounters(
                priceImport,
                priceImport.getTotalRows(),
                priceImport.getMatchedRows(),
                priceImport.getUnmatchedRows(),
                allItems
        );

        return toPreview(priceImport, allItems);
    }

    private InventoryPriceImportItem classify(
            InventoryPriceImport priceImport,
            Inventory inventory,
            PriceListRow row,
            LocalDate effectiveFrom,
            InventoryPrice current,
            InventoryPrice existingAtDate
    ) {
        BigDecimal incoming = row.retailPrice();

        BigDecimal currentAmount = current != null
                ? current.getAmount()
                : null;

        BigDecimal scheduledAmount = existingAtDate != null
                ? existingAtDate.getAmount()
                : null;

        ClassificationResult result =
                classifyPrice(
                        incoming,
                        currentAmount,
                        scheduledAmount
                );

        return InventoryPriceImportItem.builder()
                .priceImport(priceImport)
                .inventory(inventory)
                .rowNumber(row.rowNumber())
                .isbn(trim(row.isbn()))
                .title(trim(row.title()))
                .author(trim(row.authorName()))
                .incomingPrice(incoming)
                .currentPrice(currentAmount)
                .existingScheduledPrice(scheduledAmount)
                .changePercent(result.changePercent())
                .classification(result.classification())
                .conflictReason(result.reason())
                .selectedDefault(result.selectedDefault())
                .duplicateGroup(false)
                .discarded(false)
                .build();
    }

    private InventoryPriceImportItem buildAmbiguousItem(InventoryPriceImport priceImport, PriceListRow row, String reason) {
        return InventoryPriceImportItem.builder()
                .priceImport(priceImport)
                .rowNumber(row.rowNumber())
                .isbn(trim(row.isbn()))
                .title(trim(row.title()))
                .author(trim(row.authorName()))
                .incomingPrice(row.retailPrice())
                .classification(InventoryPriceImportClassification.AMBIGUOUS_MATCH)
                .conflictReason(reason)
                .selectedDefault(false)
                .build();
    }

    private void updateCounters(
            InventoryPriceImport priceImport,
            int total,
            int matched,
            int unmatched,
            List<InventoryPriceImportItem> items
    ) {
        priceImport.setTotalRows(total);
        priceImport.setMatchedRows(matched);
        priceImport.setUnmatchedRows(unmatched);
        priceImport.setNewPriceRows(count(items, InventoryPriceImportClassification.NEW_PRICE));
        priceImport.setIncreaseRows(count(items, InventoryPriceImportClassification.INCREASE));
        priceImport.setDecreaseRows(count(items, InventoryPriceImportClassification.DECREASE));
        priceImport.setUnchangedRows(count(items, InventoryPriceImportClassification.UNCHANGED));
        priceImport.setConflictRows(
                count(items, InventoryPriceImportClassification.EXISTING_SCHEDULED_CONFLICT)
                        + count(items, InventoryPriceImportClassification.DUPLICATE_CONFLICT)
                        + count(items, InventoryPriceImportClassification.AMBIGUOUS_MATCH)
                        + count(items, InventoryPriceImportClassification.INVALID_PRICE)
        );
        priceImport.setReviewRows(count(items, InventoryPriceImportClassification.LARGE_CHANGE));
    }

    private int count(List<InventoryPriceImportItem> items, InventoryPriceImportClassification classification) {
        return (int) items.stream()
                .filter(item -> !item.isDiscarded())
                .filter(item -> item.getClassification() == classification)
                .count();
    }

    private MatchIndex buildMatchIndex(List<Inventory> inventories) {
        Map<String, List<Inventory>> byIsbn = new HashMap<>();
        Map<String, List<Inventory>> byTitleAuthor = new HashMap<>();
        for (Inventory inventory : inventories) {
            addIsbnToIndex(byIsbn, inventory.getBook().getIsbn13(), inventory);
            addIsbnToIndex(byIsbn, inventory.getBook().getIsbn10(), inventory);

            String authors = inventory.getBook().getAuthors().stream().map(Author::getName).sorted().collect(Collectors.joining(" "));
            String key = titleAuthorKey(inventory.getBook().getTitle(), authors);
            if (!key.isBlank()) {
                byTitleAuthor.computeIfAbsent(key, ignored -> new ArrayList<>()).add(inventory);
            }
        }
        return new MatchIndex(byIsbn, byTitleAuthor);
    }

    private void addIsbnToIndex(Map<String, List<Inventory>> byIsbn, String value, Inventory inventory) {
        String key = isbnKey(value);
        if (!key.isBlank()) {
            byIsbn.computeIfAbsent(key, ignored -> new ArrayList<>()).add(inventory);
        }
    }

    private MatchResult match(PriceListRow row, MatchIndex index) {
        if (hasIsbn(row.isbn())) {
            String isbn = isbnKey(row.isbn());
            List<Inventory> candidates = distinct(index.byIsbn().getOrDefault(isbn, List.of()));
            if (candidates.size() == 1) {
                return new MatchResult(candidates.getFirst(), false, null);
            }
            if (candidates.size() > 1) {
                return new MatchResult(null, true, "El ISBN coincide con más de un registro de inventario (por ejemplo, distintas condiciones).");
            }

            // Si la fila informa ISBN, ese identificador es autoritativo. No debe
            // caer a título + autor, porque podría asociar el precio de otra edición.
            return new MatchResult(null, false, null);
        }

        String key = titleAuthorKey(row.title(), row.authorName());
        if (!key.isBlank()) {
            List<Inventory> candidates = distinct(index.byTitleAuthor().getOrDefault(key, List.of()));
            if (candidates.size() == 1) {
                return new MatchResult(candidates.getFirst(), false, null);
            }
            if (candidates.size() > 1) {
                return new MatchResult(null, true, "Título y autor coinciden con más de un registro de inventario.");
            }
        }
        return new MatchResult(null, false, null);
    }

    private List<Inventory> distinct(List<Inventory> candidates) {
        if (candidates.size() < 2) {
            return candidates;
        }
        return candidates.stream()
                .collect(Collectors.toMap(
                        Inventory::getId,
                        item -> item,
                        (a, b) -> a,
                        LinkedHashMap::new
                ))
                .values()
                .stream()
                .toList();
    }

    private PriceListImportConfig toParserConfig(BookstorePriceListFormat format) {
        PriceListImportConfig config = PriceListImportConfig.builder()
                .name(format.getName())
                .sheetStrategy(SheetStrategy.BY_INDEX)
                .sheetIndex(format.getSheetIndex())
                .headerStrategy(HeaderStrategy.NONE)
                .firstDataRowIndex(format.getFirstDataRowIndex())
                .active(true)
                .mappings(new ArrayList<>())
                .build();

        addMapping(config, PriceListField.ISBN, format.getIsbnColumn(), PriceListValueType.ISBN, false);
        addMapping(config, PriceListField.TITLE, format.getTitleColumn(), PriceListValueType.TEXT, false);
        addMapping(config, PriceListField.AUTHOR, format.getAuthorColumn(), PriceListValueType.TEXT, false);
        addMapping(config, PriceListField.PUBLISHER, format.getPublisherColumn(), PriceListValueType.TEXT, false);
        addMapping(config, PriceListField.RETAIL_PRICE, format.getPriceColumn(), PriceListValueType.DECIMAL, true);
        return config;
    }

    private void addMapping(
            PriceListImportConfig config,
            PriceListField field,
            Integer column,
            PriceListValueType type,
            boolean required
    ) {
        if (column == null) {
            return;
        }
        PriceListColumnMapping mapping = PriceListColumnMapping.builder()
                .targetField(field)
                .columnIndex(column)
                .valueType(type)
                .required(required)
                .active(true)
                .build();
        config.getMappings().add(mapping);
    }

    private InventoryPriceImportProviderRowJdbcRepository.StagedRow toStagedProviderRow(PriceListRow row, Long bookId) {
        return new InventoryPriceImportProviderRowJdbcRepository.StagedRow(
                row.rowNumber(), trim(row.isbn()), trim(row.title()), trim(row.authorName()), trim(row.publisherName()),
                row.retailPrice(), row.metadata() != null ? trim(row.metadata().externalCode()) : null, bookId
        );
    }

    private void writeNormalized(BufferedWriter writer, PriceListRow row) {
        try {
            writer.write(csv(row.isbn()));
            writer.write(',');
            writer.write(csv(row.title()));
            writer.write(',');
            writer.write(csv(row.authorName()));
            writer.write(',');
            writer.write(csv(row.publisherName()));
            writer.write(',');
            writer.write(row.retailPrice() != null ? row.retailPrice().toPlainString() : "");
            writer.newLine();
        } catch (Exception exception) {
            throw new BusinessException("No se pudo generar la copia normalizada de la lista.");
        }
    }

    private InventoryPriceImportPreviewResponse toPreview(InventoryPriceImport priceImport, List<InventoryPriceImportItem> items) {
        return new InventoryPriceImportPreviewResponse(
                priceImport.getId(),
                priceImport.getOriginalFilename(),
                priceImport.getFormat() != null ? priceImport.getFormat().getName() : null,
                priceImport.getProvider() != null ? priceImport.getProvider().getId() : null,
                priceImport.getProvider() != null ? priceImport.getProvider().getName() : null,
                priceImport.getSourceName(),
                priceImport.getEffectiveFrom(),
                priceImport.getStatus(),
                summary(priceImport),
                toPreviewItems(items)
        );
    }

    private List<InventoryPriceImportItemResponse> toPreviewItems(List<InventoryPriceImportItem> items) {
        Map<Long, List<InventoryPriceImportItem>> duplicatesByInventory =
                items.stream()
                        .filter(InventoryPriceImportItem::isDuplicateGroup)
                        .filter(item -> item.getInventory() != null)
                        .collect(Collectors.groupingBy(
                                item -> item.getInventory().getId(),
                                LinkedHashMap::new,
                                Collectors.toList()
                        ));

        Set<Long> emittedDuplicateInventories = new HashSet<>();

        List<InventoryPriceImportItemResponse> result = new ArrayList<>();

        for (InventoryPriceImportItem item : items) {

            if (!item.isDuplicateGroup()) {
                result.add(toItem(item));
                continue;
            }

            if (item.getInventory() == null) {
                result.add(toItem(item));
                continue;
            }

            Long inventoryId = item.getInventory().getId();

            if (!emittedDuplicateInventories.add(inventoryId)) {
                continue;
            }

            result.add(toDuplicateItem(duplicatesByInventory.getOrDefault(inventoryId, List.of(item))));
        }

        result.sort(Comparator.comparing(InventoryPriceImportItemResponse::rowNumber, Comparator.nullsLast(Integer::compareTo)));

        return result;
    }

    private InventoryPriceImportItemResponse toDuplicateItem(List<InventoryPriceImportItem> items) {
        List<InventoryPriceImportItem> ordered = items.stream()
                .sorted(Comparator.comparing(InventoryPriceImportItem::getRowNumber))
                .toList();

        InventoryPriceImportItem selected = ordered.stream()
                .filter(item -> !item.isDiscarded())
                .filter(item ->
                        item.getClassification() != InventoryPriceImportClassification.DUPLICATE_CONFLICT)
                .findFirst()
                .orElse(null);

        boolean resolved = selected != null;

        InventoryPriceImportItem representative = resolved
                ? selected
                : ordered.getFirst();

        List<InventoryPriceImportDuplicateRowResponse> duplicateRows =
                ordered.stream()
                        .map(item ->
                                new InventoryPriceImportDuplicateRowResponse(
                                        item.getId(),
                                        item.getRowNumber(),
                                        item.getIncomingPrice()
                                )
                        )
                        .toList();

        String conflictReason = getString(resolved, representative, duplicateRows);

        return new InventoryPriceImportItemResponse(
                representative.getId(),
                representative.getInventory() != null
                        ? representative.getInventory().getId()
                        : null,
                representative.getInventory() != null
                        ? representative.getInventory()
                        .getBook()
                        .getId()
                        : null,
                representative.getRowNumber(),
                representative.getIsbn(),
                representative.getTitle(),
                representative.getAuthor(),

                resolved
                        ? representative.getIncomingPrice()
                        : null,

                representative.getCurrentPrice(),
                representative.getExistingScheduledPrice(),

                resolved
                        ? representative.getChangePercent()
                        : null,

                resolved
                        ? representative.getClassification()
                        : InventoryPriceImportClassification.DUPLICATE_CONFLICT,

                conflictReason,

                resolved
                        && representative.isSelectedDefault(),
                resolved && representative.isApplied(),
                resolved
                        ? representative.getId()
                        : null,

                duplicateRows
        );
    }

    private static String getString(boolean resolved, InventoryPriceImportItem representative, List<InventoryPriceImportDuplicateRowResponse> duplicateRows) {
        String conflictReason;

        if (resolved) {
            conflictReason =
                    representative.getConflictReason();
        } else {
            boolean hasIsbn = representative.getIsbn() != null && !representative.getIsbn().isBlank();
            String subject = hasIsbn ? "El mismo ISBN" : "El libro";
            conflictReason =
                    duplicateRows.size() == 2
                            ? subject + " aparece 2 veces en la lista con precios diferentes."
                            : subject + " aparece "
                              + duplicateRows.size()
                              + " veces en la lista con precios diferentes.";
        }
        return conflictReason;
    }

    private InventoryPriceImportActivityResponse toActivity(
            InventoryPriceImport priceImport,
            List<InventoryPriceImportJobResponse> jobs
    ) {
        return new InventoryPriceImportActivityResponse(
                priceImport.getId(),
                priceImport.getFormat() != null ? priceImport.getFormat().getId() : null,
                priceImport.getFormat() != null ? priceImport.getFormat().getName() : null,
                priceImport.getProvider() != null ? priceImport.getProvider().getId() : null,
                priceImport.getProvider() != null ? priceImport.getProvider().getName() : null,
                priceImport.getSourceName(),
                priceImport.getOriginalFilename(),
                priceImport.getEffectiveFrom(),
                priceImport.getStatus(),
                summary(priceImport),
                priceImport.getCreatedAt(),
                priceImport.getAppliedAt(),
                priceImport.getProcessingStartedAt(),
                priceImport.getProcessingFinishedAt(),
                priceImport.getProcessingError(),
                jobs
        );
    }

    private InventoryPriceImportHistoryResponse toHistory(InventoryPriceImport priceImport) {
        return new InventoryPriceImportHistoryResponse(
                priceImport.getId(),
                priceImport.getOriginalFilename(),
                priceImport.getFormat() != null ? priceImport.getFormat().getName() : null,
                priceImport.getProvider() != null ? priceImport.getProvider().getId() : null,
                priceImport.getProvider() != null ? priceImport.getProvider().getName() : null,
                priceImport.getSourceName(),
                priceImport.getEffectiveFrom(),
                priceImport.getStatus(),
                summary(priceImport),
                priceImport.getCreatedAt(),
                priceImport.getAppliedAt()
        );
    }

    private InventoryPriceImportSummaryResponse summary(InventoryPriceImport priceImport) {
        return new InventoryPriceImportSummaryResponse(
                priceImport.getTotalRows(), priceImport.getMatchedRows(), priceImport.getUnmatchedRows(),
                priceImport.getNewPriceRows(), priceImport.getIncreaseRows(), priceImport.getDecreaseRows(),
                priceImport.getUnchangedRows(), priceImport.getConflictRows(), priceImport.getReviewRows(),
                priceImport.getAppliedRows()
        );
    }

    private InventoryPriceImportItemResponse toItem(InventoryPriceImportItem item) {
        return new InventoryPriceImportItemResponse(
                item.getId(),
                item.getInventory() != null ? item.getInventory().getId() : null,
                item.getInventory() != null ? item.getInventory().getBook().getId() : null,
                item.getRowNumber(),
                item.getIsbn(),
                item.getTitle(),
                item.getAuthor(),
                item.getIncomingPrice(),
                item.getCurrentPrice(),
                item.getExistingScheduledPrice(),
                item.getChangePercent(),
                item.getClassification(),
                item.getConflictReason(),
                item.isSelectedDefault(),
                item.isApplied(),
                null,
                List.of()
        );
    }

    private void markDuplicateConflict(
            InventoryPriceImportItem item,
            boolean duplicateByIsbn
    ) {
        item.setClassification(
                InventoryPriceImportClassification.DUPLICATE_CONFLICT
        );
        item.setConflictReason(
                duplicateByIsbn
                        ? "El mismo ISBN aparece más de una vez en la lista con precios diferentes."
                        : "El mismo libro aparece más de una vez en la lista con precios diferentes."
        );
        item.setSelectedDefault(false);
        item.setDuplicateGroup(true);
        item.setDiscarded(false);
    }

    private ClassificationResult classifyPrice(
            BigDecimal incoming,
            BigDecimal currentAmount,
            BigDecimal scheduledAmount
    ) {
        BigDecimal change = percent(
                currentAmount,
                incoming
        );

        if (incoming == null || incoming.signum() <= 0) {
            return new ClassificationResult(
                    InventoryPriceImportClassification.INVALID_PRICE,
                    "La fila no contiene un precio válido.",
                    false,
                    change
            );
        }

        if (scheduledAmount != null) {
            if (samePrice(scheduledAmount, incoming)) {
                return new ClassificationResult(
                        InventoryPriceImportClassification.UNCHANGED,
                        null,
                        true,
                        change
                );
            }

            return new ClassificationResult(
                    InventoryPriceImportClassification.EXISTING_SCHEDULED_CONFLICT,
                    "Ya existe un precio para la misma fecha de vigencia.",
                    false,
                    change
            );
        }

        if (currentAmount == null) {
            return new ClassificationResult(
                    InventoryPriceImportClassification.NEW_PRICE,
                    null,
                    true,
                    change
            );
        }

        if (samePrice(currentAmount, incoming)) {
            return new ClassificationResult(
                    InventoryPriceImportClassification.UNCHANGED,
                    null,
                    true,
                    change
            );
        }

        if (change != null && change.abs().compareTo(LARGE_CHANGE_PERCENT) >= 0) {
            return new ClassificationResult(
                    InventoryPriceImportClassification.LARGE_CHANGE,
                    "El cambio supera el 50% y requiere revisión.",
                    false,
                    change
            );
        }

        if (incoming.compareTo(currentAmount) > 0) {
            return new ClassificationResult(
                    InventoryPriceImportClassification.INCREASE,
                    null,
                    true,
                    change
            );
        }

        return new ClassificationResult(
                InventoryPriceImportClassification.DECREASE,
                null,
                true,
                change
        );
    }

    private InventoryPriceImport getImport(Long importId, Long bookstoreId) {
        return importRepository.findByIdAndBookstoreId(importId, bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la importación solicitada."));
    }

    private BigDecimal percent(BigDecimal current, BigDecimal incoming) {
        if (current == null || incoming == null || current.signum() == 0) {
            return null;
        }
        return incoming.subtract(current).multiply(BigDecimal.valueOf(100)).divide(current, 2, RoundingMode.HALF_UP);
    }

    private boolean samePrice(BigDecimal first, BigDecimal second) {
        return first != null && second != null && first.compareTo(second) == 0;
    }

    private boolean isCompletelyEmpty(PriceListRow row) {
        return blank(row.isbn()) && blank(row.title()) && blank(row.authorName()) && row.retailPrice() == null;
    }

    private String resolveFilename(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException("Debe seleccionar una lista de precios.");
        }
        return file.getOriginalFilename() == null ? "lista-precios.xlsx" : file.getOriginalFilename();
    }

    private String duplicateKey(PriceListRow row, Inventory inventory) {
        if (hasIsbn(row.isbn())) {
            return "ISBN:" + isbnKey(row.isbn());
        }
        return "INVENTORY:" + inventory.getId();
    }

    private boolean hasIsbn(String value) {
        return !normalizeIsbn(value).isBlank();
    }

    private String isbnKey(String value) {
        String canonical = canonicalIsbnResolver.resolve(value);
        return canonical != null ? canonical : normalizeIsbn(value);
    }

    private String normalizeIsbn(String value) {
        if (value == null) return "";
        return value.toUpperCase(Locale.ROOT).replaceAll("[^0-9X]", "");
    }

    private String titleAuthorKey(String title, String author) {
        String normalizedTitle = normalizeText(title);
        String normalizedAuthor = normalizeTokenOrder(author);
        if (normalizedTitle.isBlank() || normalizedAuthor.isBlank()) {
            return "";
        }
        return normalizedTitle + "|" + normalizedAuthor;
    }

    private String normalizeTokenOrder(String value) {
        String normalized = normalizeText(value);
        if (normalized.isBlank()) return "";
        return Arrays.stream(normalized.split("\\s+"))
                .filter(token -> !token.isBlank())
                .sorted()
                .collect(Collectors.joining(" "));
    }

    private String normalizeText(String value) {
        if (value == null) return "";
        return Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim();
    }

    private String csv(String value) {
        if (value == null) return "";
        String escaped = value.replace("\"", "\"\"");
        return "\"" + escaped + "\"";
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String resolveSourceName(String value, BookstorePriceListFormat format) {
        String normalized = normalizeSourceName(value);
        if (normalized != null) {
            return normalized;
        }
        return format != null && format.getProvider() != null
                ? normalizeSourceName(format.getProvider().getName())
                : null;
    }

    private String normalizeSourceName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= 150 ? normalized : normalized.substring(0, 150);
    }

    private void validateEffectiveFrom(LocalDate effectiveFrom) {
        if (effectiveFrom == null) {
            throw new BusinessException("Debe indicar el mes de vigencia de la lista.");
        }
        if (effectiveFrom.getDayOfMonth() != 1) {
            throw new BusinessException("Las listas de precios deben comenzar a regir el primer día del mes.");
        }
    }

    private Map<Long, InventoryPrice> currentPricesAt(Long bookstoreId, LocalDate applicationDate) {
        Map<Long, InventoryPrice> result = new LinkedHashMap<>();
        for (InventoryPrice price : priceRepository.findCurrentCandidatesForBookstore(bookstoreId, applicationDate)) {
            result.putIfAbsent(price.getInventory().getId(), price);
        }
        return result;
    }

    private Map<Long, InventoryPrice> scheduledPricesAt(Long bookstoreId, LocalDate applicationDate) {
        if (!applicationDate.isAfter(priceService.today())) {
            return Map.of();
        }

        Map<Long, InventoryPrice> result = new LinkedHashMap<>();
        for (InventoryPrice price : priceRepository.findAllByBookstoreIdAndEffectiveFrom(bookstoreId, applicationDate)) {
            result.putIfAbsent(price.getInventory().getId(), price);
        }
        return result;
    }

    private void logPreviewPhase(Long importId, String phase, long startedAtNanos, String details) {
        log.info(
                "PRICE IMPORT PREVIEW PHASE importId={} phase={} timeMs={} {}",
                importId,
                phase,
                elapsedMillis(startedAtNanos),
                details == null ? "" : details
        );
    }

    private long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    private String errorMessage(Throwable exception) {
        String message = exception instanceof BusinessException
                ? exception.getMessage()
                : "No se pudo generar la previsualización de la lista de precios.";
        if (message == null || message.isBlank()) {
            message = "No se pudo generar la previsualización de la lista de precios.";
        }
        return message.length() <= 2_000 ? message : message.substring(0, 2_000);
    }

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank() ? "formato inválido." : exception.getMessage();
    }

    private record MatchIndex(Map<String, List<Inventory>> byIsbn, Map<String, List<Inventory>> byTitleAuthor) {
    }

    private record MatchResult(Inventory inventory, boolean ambiguous, String reason) {
    }

    private record ClassificationResult(
            InventoryPriceImportClassification classification,
            String reason,
            boolean selectedDefault,
            BigDecimal changePercent
    ) {
    }
}
