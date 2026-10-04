package com.rodrilang.librarymanager.inventory.pricing.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPrice;
import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceSource;
import com.rodrilang.librarymanager.inventory.pricing.repository.InventoryPriceRepository;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class InventoryPriceOverviewServiceTest {

    @Test
    void staleStatusUsesCurrentPriceConfirmationInsteadOfInventoryCheckedDate() {
        LocalDate today = LocalDate.of(2026, 10, 4);
        BookstoreContext context = mock(BookstoreContext.class);
        InventoryRepository inventoryRepository = mock(InventoryRepository.class);
        InventoryPriceRepository priceRepository = mock(InventoryPriceRepository.class);
        InventoryPriceService priceService = mock(InventoryPriceService.class);

        Bookstore bookstore = Bookstore.builder().id(7L).build();
        Book book = Book.builder()
                .id(20L)
                .isbn13("9789875669284")
                .title("Un libro")
                .build();
        Inventory inventory = Inventory.builder()
                .id(10L)
                .bookstore(bookstore)
                .book(book)
                .active(true)
                .lastPriceCheckedAt(today)
                .build();
        InventoryPrice price = InventoryPrice.builder()
                .id(100L)
                .inventory(inventory)
                .amount(new BigDecimal("49900.00"))
                .effectiveFrom(LocalDate.of(2026, 1, 1))
                .source(InventoryPriceSource.LEGACY_MIGRATION)
                .lastConfirmedAt(LocalDate.of(2026, 6, 1))
                .lastConfirmedSource("Migración")
                .build();

        when(context.getCurrentBookstoreId()).thenReturn(7L);
        when(priceService.today()).thenReturn(today);
        when(inventoryRepository.findAllByBookstoreIdAndActiveTrue(7L)).thenReturn(List.of(inventory));
        when(priceRepository.findAllByBookstoreId(7L)).thenReturn(List.of(price));

        InventoryPriceOverviewService service = new InventoryPriceOverviewService(
                inventoryRepository,
                priceRepository,
                context,
                priceService
        );

        var page = service.find(null, "STALE", 90, PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());
        var row = page.getContent().getFirst();
        assertTrue(row.stale());
        assertFalse(row.confirmedThisMonth());
        assertEquals(LocalDate.of(2026, 6, 1), row.currentPriceLastConfirmedAt());
        assertEquals(125L, row.daysSinceConfirmation());
        assertEquals(today, row.lastPriceCheckedAt());
    }

    @Test
    void notConfirmedThisMonthExcludesBooksWithoutPrice() {
        LocalDate today = LocalDate.of(2026, 10, 4);
        BookstoreContext context = mock(BookstoreContext.class);
        InventoryRepository inventoryRepository = mock(InventoryRepository.class);
        InventoryPriceRepository priceRepository = mock(InventoryPriceRepository.class);
        InventoryPriceService priceService = mock(InventoryPriceService.class);

        Bookstore bookstore = Bookstore.builder().id(7L).build();
        Inventory withPrice = Inventory.builder()
                .id(10L)
                .bookstore(bookstore)
                .book(Book.builder().id(20L).isbn13("9789875669284").title("Con precio").build())
                .active(true)
                .build();
        Inventory withoutPrice = Inventory.builder()
                .id(11L)
                .bookstore(bookstore)
                .book(Book.builder().id(21L).isbn13("9789875669285").title("Sin precio").build())
                .active(true)
                .build();
        InventoryPrice oldConfirmation = InventoryPrice.builder()
                .id(100L)
                .inventory(withPrice)
                .amount(new BigDecimal("49900.00"))
                .effectiveFrom(LocalDate.of(2026, 8, 1))
                .source(InventoryPriceSource.MANUAL)
                .lastConfirmedAt(LocalDate.of(2026, 9, 20))
                .build();

        when(context.getCurrentBookstoreId()).thenReturn(7L);
        when(priceService.today()).thenReturn(today);
        when(inventoryRepository.findAllByBookstoreIdAndActiveTrue(7L)).thenReturn(List.of(withPrice, withoutPrice));
        when(priceRepository.findAllByBookstoreId(7L)).thenReturn(List.of(oldConfirmation));

        InventoryPriceOverviewService service = new InventoryPriceOverviewService(
                inventoryRepository,
                priceRepository,
                context,
                priceService
        );

        var page = service.find(null, "NOT_CONFIRMED_THIS_MONTH", 90, PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());
        assertEquals(10L, page.getContent().getFirst().inventoryId());
    }
}
