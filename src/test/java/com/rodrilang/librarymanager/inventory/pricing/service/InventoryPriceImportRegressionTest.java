package com.rodrilang.librarymanager.inventory.pricing.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.importer.price.configuration.parser.StreamingConfigurablePriceListParser;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import com.rodrilang.librarymanager.importer.price.storage.PriceListImportFileStorage;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeInventoryStatus;
import com.rodrilang.librarymanager.integrations.tiendanube.enums.TiendanubeSyncType;
import com.rodrilang.librarymanager.integrations.tiendanube.event.TiendanubeSyncRequestedEvent;
import com.rodrilang.librarymanager.inventory.pricing.dto.ApplyInventoryPriceImportRequest;
import com.rodrilang.librarymanager.inventory.pricing.dto.InventoryPriceImportPreviewResponse;
import com.rodrilang.librarymanager.inventory.pricing.model.BookstorePriceListFormat;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPrice;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImport;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImportClassification;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImportItem;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImportStatus;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceSource;
import com.rodrilang.librarymanager.inventory.pricing.repository.InventoryPriceImportItemRepository;
import com.rodrilang.librarymanager.inventory.pricing.repository.InventoryPriceImportRepository;
import com.rodrilang.librarymanager.inventory.pricing.repository.InventoryPriceImportProviderRowRepository;
import com.rodrilang.librarymanager.inventory.pricing.repository.InventoryPriceRepository;
import com.rodrilang.librarymanager.inventory.pricing.storage.NormalizedPriceListStorage;
import com.rodrilang.librarymanager.isbn.service.CanonicalIsbnResolver;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.BookstoreRepository;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import com.rodrilang.librarymanager.purchasing.service.BookstoreProviderPriceListService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class InventoryPriceImportRegressionTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 4);
    private static final LocalDate MONTH = TODAY.withDayOfMonth(1);
    private static final String ISBN = "9789875669284";
    private final List<InventoryPrice> prices = new ArrayList<>();
    private InventoryPriceRepository priceRepository;
    private InventoryRepository inventoryRepository;
    private BookstoreContext context;
    private ApplicationEventPublisher events;
    private InventoryPriceService service;
    private Inventory inventory;
    private InventoryPriceImport priceImport;

    @BeforeEach
    void setUp() {
        priceRepository = mock(InventoryPriceRepository.class);
        inventoryRepository = mock(InventoryRepository.class);
        context = mock(BookstoreContext.class);
        events = mock(ApplicationEventPublisher.class);
        service = spy(new InventoryPriceService(priceRepository, inventoryRepository, context, events));
        doReturn(TODAY).when(service).today();
        Bookstore bookstore = Bookstore.builder().id(7L).build();
        inventory = Inventory.builder()
                .id(10L).bookstore(bookstore)
                .book(Book.builder().id(20L).isbn13(ISBN).title("Un libro").build())
                .active(true).tiendanubeStatus(TiendanubeInventoryStatus.LINKED)
                .tiendanubePriceSyncEnabled(true).build();
        priceImport = InventoryPriceImport.builder().id(30L).bookstore(bookstore)
                .effectiveFrom(MONTH).sourceName("Editorial").status(InventoryPriceImportStatus.PROCESSING).build();

        when(context.getCurrentBookstoreId()).thenReturn(7L);
        when(context.getCurrentUserId()).thenReturn(5L);
        when(inventoryRepository.findByIdAndBookstoreId(10L, 7L)).thenReturn(Optional.of(inventory));
        when(priceRepository.findCurrentCandidates(eq(10L), any())).thenAnswer(call -> at(call.getArgument(1)));
        when(priceRepository.findCurrentCandidatesForInventoryIds(anyCollection(), any()))
                .thenAnswer(call -> at(call.getArgument(1)));
        when(priceRepository.findByInventoryIdAndEffectiveFrom(eq(10L), any())).thenAnswer(call ->
                prices.stream().filter(p -> p.getEffectiveFrom().equals(call.getArgument(1))).findFirst());
        when(priceRepository.save(any(InventoryPrice.class))).thenAnswer(call -> {
            InventoryPrice price = call.getArgument(0);
            if (!prices.contains(price)) {
                price.setId(100L + prices.size());
                prices.add(price);
            }
            return price;
        });
    }

    @Test
    void previewUsesCurrentSellingPriceEvenWhenItWasCreatedAfterMonthStart() {
        addPrice("42800", TODAY.minusDays(2), InventoryPriceSource.LEGACY_MIGRATION);
        InventoryPriceImportPreviewResponse preview = preview(MONTH, "43800");
        var item = preview.items().getFirst();
        assertEquals(new BigDecimal("42800.00"), item.currentPrice());
        assertEquals(InventoryPriceImportClassification.INCREASE, item.classification());
        assertEquals(new BigDecimal("2.34"), item.changePercent());
        assertTrue(item.selectedDefault());
        assertEquals(MONTH, preview.effectiveFrom());
    }

    @Test
    void currentMonthListUpdatesInventoryAndRequestsTiendanubePriceSync() {
        InventoryPrice previous = addPrice("42800", TODAY.minusDays(2), InventoryPriceSource.MANUAL);
        InventoryPrice imported = service.upsertImported(inventory, new BigDecimal("43800"), MONTH, priceImport, 5L);
        assertEquals(new BigDecimal("43800.00"), service.currentAmount(10L));
        assertEquals(TODAY, imported.getEffectiveFrom());
        assertEquals(MONTH, priceImport.getEffectiveFrom());
        assertEquals(new BigDecimal("42800.00"), previous.getAmount());
        assertEquals(TODAY, imported.getLastConfirmedAt());
        assertSame(priceImport, imported.getPriceImport());
        verify(events).publishEvent(new TiendanubeSyncRequestedEvent(10L, TiendanubeSyncType.PRICE));
    }

    @Test
    void sameDayManualPriceIsAnIncreaseInPreviewAndIsReplacedOnApply() {
        addPrice("42800", TODAY, InventoryPriceSource.MANUAL);
        assertEquals(InventoryPriceImportClassification.INCREASE, preview(MONTH, "43800").items().getFirst().classification());
        service.upsertImported(inventory, new BigDecimal("43800"), MONTH, priceImport, 5L);
        assertEquals(1, prices.size());
        assertEquals(new BigDecimal("43800.00"), service.currentAmount(10L));
        verify(events).publishEvent(new TiendanubeSyncRequestedEvent(10L, TiendanubeSyncType.PRICE));
    }

    @Test
    void unchangedListConfirmsCurrentPriceWithoutDuplicatingHistoryOrSyncing() {
        InventoryPrice previous = addPrice("42800", TODAY.minusDays(1), InventoryPriceSource.MANUAL);
        assertEquals(InventoryPriceImportClassification.UNCHANGED, preview(MONTH, "42800").items().getFirst().classification());
        assertSame(previous, service.upsertImported(inventory, new BigDecimal("42800"), MONTH, priceImport, 5L));
        assertEquals(1, prices.size());
        assertEquals(InventoryPriceSource.MANUAL, previous.getSource());
        assertEquals(TODAY, previous.getLastConfirmedAt());
        assertEquals("Editorial", previous.getLastConfirmedSource());
        verify(priceRepository, never()).save(any());
        verifyNoInteractions(events);
    }

    @Test
    void futureListRemainsScheduledAndDoesNotSyncEarly() {
        addPrice("42800", TODAY.minusDays(2), InventoryPriceSource.MANUAL);
        LocalDate future = MONTH.plusMonths(1);
        InventoryPrice imported = service.upsertImported(inventory, new BigDecimal("43800"), future, priceImport, 5L);
        assertEquals(future, imported.getEffectiveFrom());
        assertEquals(new BigDecimal("42800.00"), service.currentAmount(10L));
        assertEquals(new BigDecimal("43800.00"), service.priceAt(10L, future).orElseThrow().getAmount());
        assertEquals(TODAY, imported.getLastConfirmedAt());
        verifyNoInteractions(events);
    }

    @Test
    void futureListStillReportsAnExistingScheduledPriceConflict() {
        addPrice("42800", TODAY.minusDays(2), InventoryPriceSource.MANUAL);
        LocalDate future = MONTH.plusMonths(1);
        addPrice("45000", future, InventoryPriceSource.MANUAL);
        var item = preview(future, "43800").items().getFirst();
        assertEquals(InventoryPriceImportClassification.EXISTING_SCHEDULED_CONFLICT, item.classification());
        assertEquals(new BigDecimal("45000.00"), item.existingScheduledPrice());
        assertFalse(item.selectedDefault());
    }

    @Test
    void previousMonthListAppliesNowAndPreservesFutureScheduledPrices() {
        addPrice("42800", TODAY.minusDays(2), InventoryPriceSource.LEGACY_MIGRATION);
        InventoryPrice future = addPrice("46000", MONTH.plusMonths(1), InventoryPriceSource.MANUAL);
        service.upsertImported(inventory, new BigDecimal("43800"), MONTH.minusMonths(1), priceImport, 5L);
        assertEquals(new BigDecimal("43800.00"), service.currentAmount(10L));
        assertEquals(new BigDecimal("46000.00"), future.getAmount());
        assertEquals(3, prices.size());
    }

    @Test
    void disabledPriceSyncDoesNotRequestTiendanubeUpdates() {
        addPrice("42800", TODAY.minusDays(2), InventoryPriceSource.MANUAL);
        inventory.setTiendanubePriceSyncEnabled(false);
        service.upsertImported(inventory, new BigDecimal("43800"), MONTH, priceImport, 5L);
        assertEquals(new BigDecimal("43800.00"), service.currentAmount(10L));
        verifyNoInteractions(events);
    }

    @Test
    void unlinkedInventoryDoesNotRequestTiendanubeUpdates() {
        addPrice("42800", TODAY.minusDays(2), InventoryPriceSource.MANUAL);
        inventory.setTiendanubeStatus(TiendanubeInventoryStatus.NOT_PUBLISHED);
        service.upsertImported(inventory, new BigDecimal("43800"), MONTH, priceImport, 5L);
        assertEquals(new BigDecimal("43800.00"), service.currentAmount(10L));
        verifyNoInteractions(events);
    }

    @Test
    void workerRechecksRowsMarkedUnchangedInsteadOfTrustingAnOutdatedPreview() {
        addPrice("42800", TODAY.minusDays(1), InventoryPriceSource.MANUAL);
        InventoryPriceImportRepository imports = mock(InventoryPriceImportRepository.class);
        InventoryPriceImportItemRepository items = mock(InventoryPriceImportItemRepository.class);
        InventoryPriceImportItem item = InventoryPriceImportItem.builder().id(40L).inventory(inventory)
                .incomingPrice(new BigDecimal("43800")).classification(InventoryPriceImportClassification.UNCHANGED)
                .selectedForApply(true).build();
        when(imports.findById(30L)).thenReturn(Optional.of(priceImport));
        when(items.findAllByPriceImportIdOrderByRowNumberAsc(30L)).thenReturn(List.of(item));
        InventoryPriceImportProviderRowRepository providerRows = mock(InventoryPriceImportProviderRowRepository.class);
        BookstoreProviderPriceListService providerPrices = mock(BookstoreProviderPriceListService.class);
        new InventoryPriceImportApplyWorker(imports, items, providerRows, service, providerPrices).process(30L, 5L);
        assertEquals(new BigDecimal("43800.00"), service.currentAmount(10L));
        assertTrue(item.isApplied());
        assertEquals(1, priceImport.getAppliedRows());
        assertEquals(InventoryPriceImportStatus.APPLIED, priceImport.getStatus());
        verify(events).publishEvent(new TiendanubeSyncRequestedEvent(10L, TiendanubeSyncType.PRICE));
    }

    @Test
    void manualConfirmationUpdatesFreshnessWithoutChangingPriceHistory() {
        InventoryPrice current = addPrice("49900", LocalDate.of(2026, 8, 1), InventoryPriceSource.LEGACY_MIGRATION);
        current.setLastConfirmedAt(LocalDate.of(2026, 8, 1));
        current.setLastConfirmedSource("Migración");

        var response = service.confirmCurrentPrice(
                10L,
                LocalDate.of(2026, 10, 1),
                "Web de la distribuidora"
        );

        assertEquals(1, prices.size());
        assertEquals(new BigDecimal("49900.00"), current.getAmount());
        assertEquals(LocalDate.of(2026, 8, 1), current.getEffectiveFrom());
        assertEquals(LocalDate.of(2026, 10, 1), current.getLastConfirmedAt());
        assertEquals("Web de la distribuidora", current.getLastConfirmedSource());
        assertEquals(LocalDate.of(2026, 10, 1), response.lastConfirmedAt());
        verifyNoInteractions(events);
    }

    @Test
    void explicitApplySelectionCannotDropUnchangedRowsFromConfirmation() {
        InventoryPriceImportRepository imports = mock(InventoryPriceImportRepository.class);
        InventoryPriceImportItemRepository items = mock(InventoryPriceImportItemRepository.class);
        InventoryPriceImport importReady = InventoryPriceImport.builder()
                .id(30L)
                .bookstore(inventory.getBookstore())
                .effectiveFrom(MONTH)
                .sourceName("Editorial")
                .status(InventoryPriceImportStatus.PREVIEW_READY)
                .build();
        InventoryPriceImportItem unchanged = InventoryPriceImportItem.builder()
                .id(40L)
                .priceImport(importReady)
                .inventory(inventory)
                .incomingPrice(new BigDecimal("49900"))
                .classification(InventoryPriceImportClassification.UNCHANGED)
                .selectedDefault(true)
                .build();

        when(imports.findByIdAndBookstoreIdForUpdate(30L, 7L)).thenReturn(Optional.of(importReady));
        when(items.findAllByPriceImportIdOrderByRowNumberAsc(30L)).thenReturn(List.of(unchanged));

        InventoryPriceImportProviderRowRepository providerRows = mock(InventoryPriceImportProviderRowRepository.class);
        new InventoryPriceImportApplyService(context, imports, items, providerRows, events)
                .start(30L, new ApplyInventoryPriceImportRequest(List.of()));

        assertTrue(unchanged.isSelectedForApply());
        verify(items).saveAll(List.of(unchanged));
    }

    @Test
    void previewStillRecognizesBooksWithNoPriceAsNew() {
        assertEquals(InventoryPriceImportClassification.NEW_PRICE, preview(MONTH, "43800").items().getFirst().classification());
    }

    private InventoryPrice addPrice(String amount, LocalDate date, InventoryPriceSource source) {
        InventoryPrice price = InventoryPrice.builder().id(100L + prices.size()).inventory(inventory)
                .amount(new BigDecimal(amount).setScale(2)).effectiveFrom(date).source(source).build();
        prices.add(price);
        return price;
    }

    private List<InventoryPrice> at(LocalDate date) {
        return prices.stream().filter(p -> !p.getEffectiveFrom().isAfter(date))
                .sorted(Comparator.comparing(InventoryPrice::getEffectiveFrom).reversed()).toList();
    }

    private InventoryPriceImportPreviewResponse preview(LocalDate month, String incoming) {
        BookstoreRepository bookstores = mock(BookstoreRepository.class);
        BookstorePriceListFormatService formats = mock(BookstorePriceListFormatService.class);
        InventoryPriceImportRepository imports = mock(InventoryPriceImportRepository.class);
        InventoryPriceImportItemRepository items = mock(InventoryPriceImportItemRepository.class);
        PriceListImportFileStorage files = mock(PriceListImportFileStorage.class);
        StreamingConfigurablePriceListParser parser = mock(StreamingConfigurablePriceListParser.class);
        NormalizedPriceListStorage normalized = mock(NormalizedPriceListStorage.class);
        CanonicalIsbnResolver isbns = mock(CanonicalIsbnResolver.class);
        when(context.getCurrentBookstoreId()).thenReturn(7L);
        when(bookstores.findById(7L)).thenReturn(Optional.of(inventory.getBookstore()));
        when(inventoryRepository.findAllByBookstoreIdAndActiveTrue(7L)).thenReturn(List.of(inventory));
        when(formats.getForCurrentBookstore(1L)).thenReturn(BookstorePriceListFormat.builder()
                .name("ISBN y precio").sheetIndex(0).firstDataRowIndex(1).isbnColumn(0).priceColumn(1).build());
        when(imports.save(any())).thenAnswer(call -> {
            InventoryPriceImport value = call.getArgument(0);
            value.setId(30L);
            return value;
        });
        when(files.store(any())).thenReturn(Path.of("unused-mocked-source.xlsx"));
        when(normalized.upload(eq(7L), any())).thenReturn(new NormalizedPriceListStorage.StoredRawFile("list", "https://example.com/list.csv"));
        when(isbns.resolve(ISBN)).thenReturn(ISBN);
        doAnswer(call -> {
            Consumer<PriceListRow> consumer = call.getArgument(2);
            consumer.accept(new PriceListRow(2, ISBN, "Un libro", null, "Editorial", new BigDecimal(incoming), null, null, null));
            return null;
        }).when(parser).parse(any(), any(), any());
        InventoryPriceImportProviderRowRepository providerRows = mock(InventoryPriceImportProviderRowRepository.class);
        InventoryPriceImportService importer = new InventoryPriceImportService(context, bookstores,
                inventoryRepository, priceRepository, service, formats, imports, items, files, parser, normalized, isbns, providerRows);
        return importer.preview(1L, "Editorial", month,
                new MockMultipartFile("file", "lista.xlsx", "application/octet-stream", new byte[]{1}));
    }
}
