package com.rodrilang.librarymanager.inventory.pricing.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.importer.price.configuration.parser.StreamingConfigurablePriceListParser;
import com.rodrilang.librarymanager.importer.price.dto.internal.PriceListRow;
import com.rodrilang.librarymanager.importer.price.storage.PriceListImportFileStorage;
import com.rodrilang.librarymanager.inventory.pricing.model.BookstorePriceListFormat;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImport;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImportStatus;
import com.rodrilang.librarymanager.inventory.pricing.repository.*;
import com.rodrilang.librarymanager.inventory.pricing.job.service.InventoryPriceImportJobService;
import com.rodrilang.librarymanager.inventory.pricing.storage.NormalizedPriceListStorage;
import com.rodrilang.librarymanager.isbn.service.CanonicalIsbnResolver;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.repository.BookstoreRepository;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryPriceImportProviderBatchTest {
    @Test
    void providerCatalogRowsAreStagedInBoundedBatches() {
        BookstoreContext context = mock(BookstoreContext.class);
        BookstoreRepository bookstores = mock(BookstoreRepository.class);
        InventoryRepository inventories = mock(InventoryRepository.class);
        InventoryPriceRepository prices = mock(InventoryPriceRepository.class);
        InventoryPriceService priceService = mock(InventoryPriceService.class);
        BookstorePriceListFormatService formats = mock(BookstorePriceListFormatService.class);
        InventoryPriceImportRepository imports = mock(InventoryPriceImportRepository.class);
        InventoryPriceImportItemRepository items = mock(InventoryPriceImportItemRepository.class);
        PriceListImportFileStorage files = mock(PriceListImportFileStorage.class);
        StreamingConfigurablePriceListParser parser = mock(StreamingConfigurablePriceListParser.class);
        NormalizedPriceListStorage normalized = mock(NormalizedPriceListStorage.class);
        CanonicalIsbnResolver isbns = mock(CanonicalIsbnResolver.class);
        InventoryPriceImportProviderRowJdbcRepository staging = mock(InventoryPriceImportProviderRowJdbcRepository.class);
        InventoryPriceImportJobService jobs = mock(InventoryPriceImportJobService.class);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        InventoryPriceImportProgressService progress = mock(InventoryPriceImportProgressService.class);

        Bookstore bookstore = Bookstore.builder().id(7L).build();
        Provider provider = Provider.builder().id(5L).name("Proveedor").code("TEST").build();
        BookstorePriceListFormat format = BookstorePriceListFormat.builder()
                .id(1L).bookstore(bookstore).sheetIndex(0).firstDataRowIndex(1)
                .isbnColumn(0).priceColumn(1).active(true).build();
        Path source = Path.of("mock-provider-price-list.xlsx");
        AtomicReference<InventoryPriceImport> stored = new AtomicReference<>();
        List<Integer> sizes = new ArrayList<>();
        List<Integer> firstRows = new ArrayList<>();

        when(context.getCurrentBookstoreId()).thenReturn(7L);
        when(bookstores.findById(7L)).thenReturn(Optional.of(bookstore));
        when(formats.getForCurrentBookstore(1L)).thenReturn(format);
        when(formats.resolveImportProvider(5L)).thenReturn(provider);
        when(files.store(any())).thenReturn(source);
        when(inventories.findAllByBookstoreIdAndActiveTrue(7L)).thenReturn(List.of());
        when(priceService.importApplicationDate(any())).thenAnswer(call -> call.getArgument(0));
        when(priceService.today()).thenReturn(LocalDate.of(2026, 10, 24));
        when(prices.findCurrentCandidatesForBookstore(eq(7L), any())).thenReturn(List.of());
        when(prices.findAllByBookstoreIdAndEffectiveFrom(eq(7L), any())).thenReturn(List.of());
        when(imports.save(any())).thenAnswer(call -> {
            InventoryPriceImport value = call.getArgument(0);
            value.setId(30L);
            stored.set(value);
            return value;
        });
        when(imports.findById(30L)).thenAnswer(call -> Optional.ofNullable(stored.get()));
        when(normalized.upload(eq(7L), any())).thenReturn(
                new NormalizedPriceListStorage.StoredRawFile("list", "https://example.com/list.csv"));
        doAnswer(call -> {
            @SuppressWarnings("unchecked")
            Consumer<PriceListRow> consumer = call.getArgument(2);
            for (int i = 1; i <= 1201; i++) {
                consumer.accept(new PriceListRow(i, null, "Libro " + i, "Autor", "Editorial",
                        BigDecimal.valueOf(i), null, null, null));
            }
            return null;
        }).when(parser).parse(any(), any(), any());
        doAnswer(call -> {
            @SuppressWarnings("unchecked")
            List<InventoryPriceImportProviderRowJdbcRepository.StagedRow> batch = call.getArgument(1);
            sizes.add(batch.size());
            firstRows.add(batch.getFirst().rowNumber());
            return null;
        }).when(staging).insertAll(eq(30L), anyList());

        InventoryPriceImportService importer = new InventoryPriceImportService(
                context, bookstores, inventories, prices, priceService, formats, imports,
                items, files, parser, normalized, isbns, staging, jobs, events, progress);
        var started = importer.startPreview(1L, "Lista", LocalDate.of(2026, 10, 1),
                new MockMultipartFile("file", "lista.xlsx", "application/octet-stream", new byte[] {1}),
                5L, false, true);
        importer.processPreview(started.importId(), source);

        assertEquals(List.of(500, 500, 201), sizes);
        assertEquals(List.of(1, 501, 1001), firstRows);
        assertEquals(1201, stored.get().getTotalRows());
        assertEquals(InventoryPriceImportStatus.PREVIEW_READY, stored.get().getStatus());
        verify(jobs).ensureProviderCatalogSync(30L, 1201);
    }
}
