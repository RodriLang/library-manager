package com.rodrilang.librarymanager.inventory.count.service;

import com.rodrilang.librarymanager.editorialprice.service.EffectiveEditorialPriceService;
import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountItem;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InventoryCountPriceResolver {

    private final InventoryRepository inventoryRepository;
    private final EffectiveEditorialPriceService editorialPriceService;

    public boolean requiresPrice(Book book, Long bookstoreId, BookCondition condition) {
        if (inventoryRepository.findByBookIdAndBookstoreIdAndCondition(book.getId(), bookstoreId, condition).isPresent()) {
            return false;
        }

        return editorialPriceService.findCurrentByBookId(book.getId()).isEmpty();
    }

    public Optional<BigDecimal> resolve(InventoryCountItem item) {
        if (item.getSalePriceOverride() != null) {
            return Optional.of(item.getSalePriceOverride());
        }

        Inventory inventory = inventoryRepository.findByBookIdAndBookstoreIdAndCondition(
                item.getBook().getId(),
                item.getSession().getBookstore().getId(),
                item.getSession().getCondition()
        ).orElse(null);

        if (inventory != null) {
            return Optional.of(inventory.getSalePrice());
        }

        return editorialPriceService.findCurrentByBookId(item.getBook().getId()).map(price -> price.getPrice());
    }
}
