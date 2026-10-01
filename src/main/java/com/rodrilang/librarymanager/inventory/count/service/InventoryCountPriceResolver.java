package com.rodrilang.librarymanager.inventory.count.service;

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

    public boolean requiresPrice(Book book, Long bookstoreId, BookCondition condition) {
        return false;
    }

    /** @deprecated el flujo de carga ya no depende de precios editoriales */
    @Deprecated
    public Optional<BigDecimal> currentEditorialPrice(Book book) {
        return Optional.empty();
    }

    /** @deprecated el flujo de carga ya no depende de precios editoriales */
    @Deprecated
    public Map<Long, BigDecimal> currentEditorialPrices(Collection<Long> bookIds) {
        return Map.of();
    }

    public Optional<Inventory> existingInventory(InventoryCountItem item) {
        if (item.getBook() == null) return Optional.empty();
        return inventoryRepository.findByBookIdAndBookstoreIdAndCondition(
                item.getBook().getId(), item.getSession().getBookstore().getId(), item.getSession().getCondition());
    }

    public Map<Long, Inventory> existingInventories(InventoryCountSession session, Collection<Long> bookIds) {
        if (bookIds == null || bookIds.isEmpty()) return Map.of();
        return inventoryRepository.findAllByBookstoreIdAndBookIdInAndCondition(
                        session.getBookstore().getId(), bookIds, session.getCondition())
                .stream().collect(Collectors.toMap(inventory -> inventory.getBook().getId(), Function.identity()));
    }

    public Optional<BigDecimal> resolve(InventoryCountItem item) {
        if (item.getSalePriceOverride() != null) return Optional.of(item.getSalePriceOverride());
        return existingInventory(item).map(Inventory::getSalePrice).filter(java.util.Objects::nonNull);
    }
}
