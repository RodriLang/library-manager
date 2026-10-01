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
import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.provider.repository.ProviderRepository;
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
    private final ProviderRepository providerRepository;
    private final PriceListImportFileStorage fileStorage;
    private final StreamingConfigurablePriceListParser parser;
    private final NormalizedPriceListStorage normalizedStorage;

    @Transactional
    public InventoryPriceImportPreviewResponse preview(
            Long formatId,
            Long providerId,
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
        Provider provider = providerId == null ? null : providerRepository.findById(providerId)
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el proveedor seleccionado."));

        InventoryPriceImport priceImport = importRepository.save(InventoryPriceImport.builder()
                .bookstore(bookstore)
                .format(format)
                .provider(provider)
                .originalFilename(resolveFilename(file))
                .effectiveFrom(effectiveFrom)
                .status(InventoryPriceImportStatus.PREVIEW_READY)
                .createdByUserId(bookstoreContext.getCurrentUserId())
                .build());

        List<Inventory> inventories = inventoryRepository.findAllByBookstoreIdAndActiveTrue(bookstoreId);
        MatchIndex matchIndex = buildMatchIndex(inventories);
        Map<Long, InventoryPrice> currentPrices = priceService.currentFor(inventories.stream().map(Inventory::getId).toList());
        Map<Long, InventoryPriceImportItem> firstItemByInventory = new HashMap<>();
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

                    InventoryPriceImportItem first = firstItemByInventory.get(inventory.getId());
                    if (first != null) {
                        if (samePrice(first.getIncomingPrice(), item.getIncomingPrice())) {
                            return;
                        }
                        first.setClassification(InventoryPriceImportClassification.DUPLICATE_CONFLICT);
                        first.setSelectedDefault(false);
                        first.setConflictReason("El mismo libro aparece más de una vez en la lista con precios diferentes.");
                        item.setClassification(InventoryPriceImportClassification.DUPLICATE_CONFLICT);
                        item.setSelectedDefault(false);
                        item.setConflictReason("El mismo libro aparece más de una vez en la lista con precios diferentes.");
                    } else {
                        firstItemByInventory.put(inventory.getId(), item);
                    }
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

    @Transactional
    public InventoryPriceImportApplyResponse apply(Long importId, ApplyInventoryPriceImportRequest request) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        InventoryPriceImport priceImport = getImport(importId, bookstoreId);
        if (priceImport.getStatus() != InventoryPriceImportStatus.PREVIEW_READY) {
            throw new BusinessException("Esta importación ya no se encuentra pendiente de aplicación.");
        }

        List<InventoryPriceImportItem> allItems = itemRepository.findAllByPriceImportIdOrderByRowNumberAsc(importId);
        Set<Long> requestedIds = request != null && request.itemIds() != null
                ? new HashSet<>(request.itemIds())
                : allItems.stream().filter(InventoryPriceImportItem::isSelectedDefault).map(InventoryPriceImportItem::getId).collect(Collectors.toSet());

        int applied = 0;
        int skipped = 0;
        Long userId = bookstoreContext.getCurrentUserId();

        for (InventoryPriceImportItem item : allItems) {
            if (item.getInventory() == null) {
                skipped++;
                continue;
            }

            if (item.getClassification() == InventoryPriceImportClassification.UNCHANGED) {
                priceService.confirmPrice(item.getInventory());
                continue;
            }

            if (!requestedIds.contains(item.getId())) {
                skipped++;
                continue;
            }

            if (item.getIncomingPrice() == null || item.getIncomingPrice().signum() <= 0) {
                skipped++;
                continue;
            }

            if (item.getClassification() == InventoryPriceImportClassification.DUPLICATE_CONFLICT
                    || item.getClassification() == InventoryPriceImportClassification.AMBIGUOUS_MATCH) {
                skipped++;
                continue;
            }

            priceService.upsertImported(
                    item.getInventory(),
                    item.getIncomingPrice(),
                    priceImport.getEffectiveFrom(),
                    priceImport,
                    userId
            );
            applied++;
        }

        priceImport.setAppliedRows(applied);
        priceImport.setAppliedAt(Instant.now());
        priceImport.setStatus(InventoryPriceImportStatus.APPLIED);

        return new InventoryPriceImportApplyResponse(importId, applied, skipped);
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

    private InventoryPriceImportItem classify(
            InventoryPriceImport priceImport,
            Inventory inventory,
            PriceListRow row,
            LocalDate effectiveFrom,
            InventoryPrice current
    ) {
        BigDecimal incoming = row.retailPrice();
        BigDecimal currentAmount = current != null ? current.getAmount() : null;
        InventoryPrice existingAtDate = priceRepository.findByInventoryIdAndEffectiveFrom(inventory.getId(), effectiveFrom).orElse(null);
        BigDecimal scheduledAmount = existingAtDate != null ? existingAtDate.getAmount() : null;

        InventoryPriceImportClassification classification;
        String reason = null;
        boolean selected = true;
        BigDecimal change = percent(currentAmount, incoming);

        if (incoming == null || incoming.signum() <= 0) {
            classification = InventoryPriceImportClassification.INVALID_PRICE;
            reason = "La fila no contiene un precio válido.";
            selected = false;
        } else if (existingAtDate != null) {
            if (samePrice(existingAtDate.getAmount(), incoming)) {
                classification = InventoryPriceImportClassification.UNCHANGED;
                selected = false;
            } else {
                classification = InventoryPriceImportClassification.EXISTING_SCHEDULED_CONFLICT;
                reason = "Ya existe un precio para la misma fecha de vigencia.";
                selected = false;
            }
        } else if (currentAmount == null) {
            classification = InventoryPriceImportClassification.NEW_PRICE;
        } else if (samePrice(currentAmount, incoming)) {
            classification = InventoryPriceImportClassification.UNCHANGED;
            selected = false;
        } else if (change != null && change.abs().compareTo(LARGE_CHANGE_PERCENT) >= 0) {
            classification = InventoryPriceImportClassification.LARGE_CHANGE;
            reason = "El cambio supera el 50% y requiere revisión.";
            selected = false;
        } else if (incoming.compareTo(currentAmount) > 0) {
            classification = InventoryPriceImportClassification.INCREASE;
        } else {
            classification = InventoryPriceImportClassification.DECREASE;
        }

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
                .changePercent(change)
                .classification(classification)
                .conflictReason(reason)
                .selectedDefault(selected)
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
        return (int) items.stream().filter(item -> item.getClassification() == classification).count();
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
                return new MatchResult(candidates.get(0), false, null);
            }
            if (candidates.size() > 1) {
                return new MatchResult(null, true, "El ISBN coincide con más de un registro de inventario (por ejemplo, distintas condiciones)." );
            }
        }

        String key = titleAuthorKey(row.title(), row.authorName());
        if (!key.isBlank()) {
            List<Inventory> candidates = distinct(index.byTitleAuthor().getOrDefault(key, List.of()));
            if (candidates.size() == 1) {
                return new MatchResult(candidates.get(0), false, null);
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
                .importConfig(config)
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
                priceImport.getProvider() != null ? priceImport.getProvider().getName() : null,
                priceImport.getEffectiveFrom(),
                priceImport.getStatus(),
                summary(priceImport),
                items.stream().map(this::toItem).toList()
        );
    }

    private InventoryPriceImportHistoryResponse toHistory(InventoryPriceImport priceImport) {
        return new InventoryPriceImportHistoryResponse(
                priceImport.getId(),
                priceImport.getOriginalFilename(),
                priceImport.getFormat() != null ? priceImport.getFormat().getName() : null,
                priceImport.getProvider() != null ? priceImport.getProvider().getName() : null,
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
                item.getRowNumber(), item.getIsbn(), item.getTitle(), item.getAuthor(), item.getIncomingPrice(),
                item.getCurrentPrice(), item.getExistingScheduledPrice(), item.getChangePercent(),
                item.getClassification(), item.getConflictReason(), item.isSelectedDefault()
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

    private String safeMessage(Exception exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank() ? "formato inválido." : exception.getMessage();
    }

    private record MatchIndex(Map<String, List<Inventory>> byIsbn, Map<String, List<Inventory>> byTitleAuthor) {
    }

    private record MatchResult(Inventory inventory, boolean ambiguous, String reason) {
    }
}
