package com.rodrilang.librarymanager.inventory.count.service;

import com.rodrilang.librarymanager.editorialprice.model.EffectiveEditorialPrice;
import com.rodrilang.librarymanager.editorialprice.service.EffectiveEditorialPriceService;
import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItem;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountSession;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class InventoryCountPriceResolver {

    private final InventoryRepository inventoryRepository;
    private final EffectiveEditorialPriceService editorialPriceService;

    public boolean requiresPrice(Book book, Long bookstoreId, BookCondition condition) {
        if (inventoryRepository.findByBookIdAndBookstoreIdAndCondition(book.getId(), bookstoreId, condition).isPresent()) {
            return false;
        }
        return currentEditorialPrice(book).isEmpty();
    }

    public Optional<BigDecimal> currentEditorialPrice(Book book) {
        return editorialPriceService.findCurrentByBookId(book.getId()).map(EffectiveEditorialPrice::getPrice);
    }

    public Map<Long, BigDecimal> currentEditorialPrices(Collection<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return Map.of();
        }
        return editorialPriceService.findCurrentByBookIds(bookIds).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue().getPrice()));
    }

    public Optional<Inventory> existingInventory(InventoryCountItem item) {
        if (item.getBook() == null) {
            return Optional.empty();
        }
        return inventoryRepository.findByBookIdAndBookstoreIdAndCondition(
                item.getBook().getId(),
                item.getSession().getBookstore().getId(),
                item.getSession().getCondition()
        );
    }

    public Map<Long, Inventory> existingInventories(InventoryCountSession session, Collection<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) {
            return Map.of();
        }
        return inventoryRepository.findAllByBookstoreIdAndBookIdInAndCondition(
                        session.getBookstore().getId(),
                        bookIds,
                        session.getCondition()
                ).stream()
                .collect(Collectors.toMap(inventory -> inventory.getBook().getId(), Function.identity()));
    }

    public Optional<BigDecimal> resolve(InventoryCountItem item) {
        if (item.getSalePriceOverride() != null) {
            return Optional.of(item.getSalePriceOverride());
        }

        Optional<Inventory> inventory = existingInventory(item);
        if (inventory.isPresent()) {
            return Optional.of(inventory.get().getSalePrice());
        }

        return currentEditorialPrice(item.getBook());
    }
}
