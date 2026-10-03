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
import com.rodrilang.librarymanager.inventory.pricing.model.*;
import com.rodrilang.librarymanager.inventory.pricing.repository.InventoryPriceImportItemRepository;
import com.rodrilang.librarymanager.inventory.pricing.repository.InventoryPriceImportRepository;
import com.rodrilang.librarymanager.inventory.pricing.repository.InventoryPriceRepository;
import com.rodrilang.librarymanager.inventory.pricing.storage.NormalizedPriceListStorage;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.BookstoreRepository;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedWriter;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
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

    @Transactional
    public InventoryPriceImportPreviewResponse preview(
            Long formatId,
            String sourceName,
            LocalDate effectiveFrom,
            MultipartFile file
    ) {
        if (effectiveFrom == null) {
            throw new BusinessException("Debe indicar el mes de vigencia de la lista.");
        }
        if (effectiveFrom.getDayOfMonth() != 1) {
            throw new BusinessException("Las listas de precios deben comenzar a regir el primer día del mes.");
        }

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        Bookstore bookstore = bookstoreRepository.findById(bookstoreId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró la librería seleccionada."));
        BookstorePriceListFormat format = formatService.getForCurrentBookstore(formatId);
        InventoryPriceImport priceImport = importRepository.save(InventoryPriceImport.builder()
                .bookstore(bookstore)
                .format(format)
                .sourceName(normalizeSourceName(sourceName))
                .originalFilename(resolveFilename(file))
                .effectiveFrom(effectiveFrom)
                .status(InventoryPriceImportStatus.PREVIEW_READY)
                .createdByUserId(bookstoreContext.getCurrentUserId())
                .build());

        List<Inventory> inventories = inventoryRepository.findAllByBookstoreIdAndActiveTrue(bookstoreId);
        MatchIndex matchIndex = buildMatchIndex(inventories);
        Map<Long, InventoryPrice> currentPrices = priceService.pricesAt(inventories.stream()
                .map(Inventory::getId).toList(), effectiveFrom);
        Map<Long, List<InventoryPriceImportItem>> itemsByInventory = new HashMap<>();
        List<InventoryPriceImportItem> items = new ArrayList<>();

        AtomicInteger total = new AtomicInteger();
        AtomicInteger matched = new AtomicInteger();
        AtomicInteger unmatched = new AtomicInteger();

        Path source = fileStorage.store(file);
        Path normalizedFile = null;
        try {
            normalizedFile = Files.createTempFile("anaquel-price-list-", ".csv");
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
                            effectiveFrom,
                            currentPrices.get(inventory.getId())
                    );

                    List<InventoryPriceImportItem> existingItems =
                            itemsByInventory.computeIfAbsent(inventory.getId(), ignored -> new ArrayList<>());

                    boolean samePriceAlreadyPresent = existingItems.stream()
                            .anyMatch(existing -> samePrice(
                                            existing.getIncomingPrice(),
                                            item.getIncomingPrice()
                                    )
                            );

                    if (samePriceAlreadyPresent) {
                        return;
                    }

                    if (!existingItems.isEmpty()) {
                        for (InventoryPriceImportItem existing : existingItems) {
                            markDuplicateConflict(existing);
                        }

                        markDuplicateConflict(item);
                    }

                    existingItems.add(item);
                    items.add(item);
                });
            }

            itemRepository.saveAll(items);
            updateCounters(priceImport, total.get(), matched.get(), unmatched.get(), items);

            try {
                NormalizedPriceListStorage.StoredRawFile stored = normalizedStorage.upload(bookstoreId, normalizedFile);
                priceImport.setNormalizedFilePublicId(stored.publicId());
                priceImport.setNormalizedFileUrl(stored.secureUrl());
            } catch (RuntimeException storageException) {
                log.warn("No se pudo guardar la copia normalizada de la lista {}", priceImport.getId(), storageException);
            }

            return toPreview(priceImport, items);
        } catch (BusinessException exception) {
            priceImport.setStatus(InventoryPriceImportStatus.FAILED);
            throw exception;
        } catch (Exception exception) {
            priceImport.setStatus(InventoryPriceImportStatus.FAILED);
            throw new BusinessException("No se pudo procesar la lista de precios: " + safeMessage(exception));
        } finally {
            fileStorage.deleteQuietly(source);
            if (normalizedFile != null) {
                try {
                    Files.deleteIfExists(normalizedFile);
                } catch (Exception ignored) {
                }
            }
        }
    }

    @Transactional(readOnly = true)
    public InventoryPriceImportPreviewResponse get(Long importId) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        InventoryPriceImport priceImport = getImport(importId, bookstoreId);
        return toPreview(priceImport, itemRepository.findAllByPriceImportIdOrderByRowNumberAsc(importId));
    }

    @Transactional(readOnly = true)
    public PageResponse<InventoryPriceImportHistoryResponse> history(Pageable pageable) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        Page<InventoryPriceImportHistoryResponse> page = importRepository
                .findAllByBookstoreIdOrderByCreatedAtDesc(bookstoreId, pageable)
                .map(this::toHistory);
        return PageResponse.of(page);
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
            InventoryPrice current
    ) {
        BigDecimal incoming = row.retailPrice();

        BigDecimal currentAmount = current != null
                ? current.getAmount()
                : null;

        InventoryPrice existingAtDate = priceRepository.findByInventoryIdAndEffectiveFrom(inventory.getId(), effectiveFrom)
                .orElse(null);

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
            if (inventory.getBook().getIsbn13() != null) {
                byIsbn.computeIfAbsent(normalizeIsbn(inventory.getBook().getIsbn13()), ignored -> new ArrayList<>()).add(inventory);
            }
            if (inventory.getBook().getIsbn10() != null) {
                byIsbn.computeIfAbsent(normalizeIsbn(inventory.getBook().getIsbn10()), ignored -> new ArrayList<>()).add(inventory);
            }
            String authors = inventory.getBook().getAuthors().stream().map(Author::getName).sorted().collect(Collectors.joining(" "));
            String key = titleAuthorKey(inventory.getBook().getTitle(), authors);
            if (!key.isBlank()) {
                byTitleAuthor.computeIfAbsent(key, ignored -> new ArrayList<>()).add(inventory);
            }
        }
        return new MatchIndex(byIsbn, byTitleAuthor);
    }

    private MatchResult match(PriceListRow row, MatchIndex index) {
        String isbn = normalizeIsbn(row.isbn());
        if (!isbn.isBlank()) {
            List<Inventory> candidates = distinct(index.byIsbn().getOrDefault(isbn, List.of()));
            if (candidates.size() == 1) {
                return new MatchResult(candidates.getFirst(), false, null);
            }
            if (candidates.size() > 1) {
                return new MatchResult(null, true, "El ISBN coincide con más de un registro de inventario (por ejemplo, distintas condiciones).");
            }
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
        return candidates.stream().collect(Collectors.toMap(Inventory::getId, item -> item, (a, b) -> a, LinkedHashMap::new)).values().stream().toList();
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
            conflictReason =
                    duplicateRows.size() == 2
                            ? "El libro aparece 2 veces en la lista con precios diferentes."
                            : "El libro aparece "
                              + duplicateRows.size()
                              + " veces en la lista con precios diferentes.";
        }
        return conflictReason;
    }

    private InventoryPriceImportHistoryResponse toHistory(InventoryPriceImport priceImport) {
        return new InventoryPriceImportHistoryResponse(
                priceImport.getId(),
                priceImport.getOriginalFilename(),
                priceImport.getFormat() != null ? priceImport.getFormat().getName() : null,
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
            InventoryPriceImportItem item
    ) {
        item.setClassification(
                InventoryPriceImportClassification.DUPLICATE_CONFLICT
        );
        item.setConflictReason(
                "El mismo libro aparece más de una vez en la lista con precios diferentes."
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

    private String normalizeSourceName(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= 150 ? normalized : normalized.substring(0, 150);
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
