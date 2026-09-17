package com.rodrilang.librarymanager.purchasing.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.editorialprice.service.EffectiveEditorialPriceService;
import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.inventory.cost.service.InventoryCostCalculator;
import com.rodrilang.librarymanager.isbn.model.ParsedIsbn;
import com.rodrilang.librarymanager.isbn.service.IsbnService;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.purchasing.dto.request.CreatePurchaseRequest;
import com.rodrilang.librarymanager.purchasing.dto.request.UpsertPurchaseItemRequest;
import com.rodrilang.librarymanager.purchasing.dto.request.ScanPurchaseItemRequest;
import com.rodrilang.librarymanager.purchasing.dto.response.PurchaseResponse;
import com.rodrilang.librarymanager.purchasing.model.*;
import com.rodrilang.librarymanager.purchasing.repository.PurchaseItemRepository;
import com.rodrilang.librarymanager.purchasing.repository.PurchaseRepository;
import com.rodrilang.librarymanager.repository.BookRepository;
import com.rodrilang.librarymanager.repository.BookstoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PurchaseService {
    private final PurchaseRepository purchaseRepository;
    private final PurchaseItemRepository purchaseItemRepository;
    private final BookRepository bookRepository;
    private final BookstoreRepository bookstoreRepository;
    private final SupplierService supplierService;
    private final BookSupplierTermService termService;
    private final PurchaseInventoryService purchaseInventoryService;
    private final EffectiveEditorialPriceService editorialPriceService;
    private final InventoryCostCalculator calculator;
    private final IsbnService isbnService;
    private final BookstoreContext bookstoreContext;
    private final PurchasingMapper mapper;

    @Transactional(readOnly = true)
    public List<PurchaseResponse> findAll() {
        return purchaseRepository.findAllByBookstoreIdOrderByPurchaseDateDescIdDesc(bookstoreContext.getCurrentBookstoreId())
                .stream().map(this::loadAndMap).toList();
    }

    @Transactional(readOnly = true)
    public PurchaseResponse findById(Long purchaseId) {
        return mapper.toResponse(requirePurchase(purchaseId));
    }

    @Transactional
    public PurchaseResponse create(CreatePurchaseRequest request) {
        Supplier supplier = supplierService.requireSupplier(request.supplierId());
        Bookstore bookstore = bookstoreRepository.findById(bookstoreContext.getCurrentBookstoreId())
                .orElseThrow(() -> new BusinessException("Librería no encontrada"));
        Purchase purchase = purchaseRepository.save(Purchase.builder()
                .bookstore(bookstore)
                .supplier(supplier)
                .purchaseDate(request.purchaseDate())
                .documentNumber(clean(request.documentNumber()))
                .notes(clean(request.notes()))
                .status(PurchaseStatus.DRAFT)
                .totalAmount(BigDecimal.ZERO.setScale(2))
                .build());
        return mapper.toResponse(purchase);
    }

    @Transactional
    public PurchaseResponse scanItem(Long purchaseId, ScanPurchaseItemRequest request) {
        Purchase purchase = requireDraft(purchaseId);
        ParsedIsbn parsed = isbnService.parse(request.code());
        if (!parsed.valid()) {
            throw new BusinessException("El código escaneado no es un ISBN válido");
        }

        Book book = bookRepository.findByIsbn13(parsed.isbn13())
                .or(() -> parsed.isbn10() == null ? java.util.Optional.empty() : bookRepository.findByIsbn10(parsed.isbn10()))
                .orElseThrow(() -> new BusinessException("El ISBN escaneado no existe en el catálogo"));

        PurchaseItem item = purchase.getItems().stream()
                .filter(existing -> existing.getBook().getId().equals(book.getId()) && existing.getCondition() == BookCondition.NEW)
                .findFirst()
                .orElse(null);

        if (item == null) {
            BigDecimal editorialPrice = editorialPriceService.findCurrentByBookId(book.getId())
                    .map(price -> price.getPrice())
                    .orElse(null);
            item = PurchaseItem.builder()
                    .purchase(purchase)
                    .book(book)
                    .condition(BookCondition.NEW)
                    .quantity(1)
                    .editorialPriceSnapshot(editorialPrice)
                    .build();
            purchase.getItems().add(item);
        } else {
            item.setQuantity(item.getQuantity() + 1);
            if (item.getUnitCost() != null) {
                item.setTotalCost(item.getUnitCost().multiply(BigDecimal.valueOf(item.getQuantity())).setScale(2));
            }
        }

        purchaseItemRepository.save(item);
        recalculate(purchase);
        return mapper.toResponse(purchase);
    }

    @Transactional
    public PurchaseResponse upsertItem(Long purchaseId, UpsertPurchaseItemRequest request) {
        Purchase purchase = requireDraft(purchaseId);
        Book book = bookRepository.findById(request.bookId())
                .orElseThrow(() -> new BusinessException("Libro no encontrado"));
        BookCondition condition = request.condition() == null ? BookCondition.NEW : request.condition();

        PurchaseItem item = purchase.getItems().stream()
                .filter(existing -> existing.getBook().getId().equals(book.getId()) && existing.getCondition() == condition)
                .findFirst()
                .orElseGet(() -> {
                    PurchaseItem created = PurchaseItem.builder().purchase(purchase).book(book).condition(condition).build();
                    purchase.getItems().add(created);
                    return created;
                });

        BigDecimal editorialPrice = calculator.money(request.editorialPriceSnapshot());
        if (editorialPrice == null) {
            editorialPrice = editorialPriceService.findCurrentByBookId(book.getId()).map(p -> p.getPrice()).orElse(null);
        }
        BigDecimal unitCost = calculator.money(request.unitCost());
        BigDecimal discount = calculator.percentage(request.discountPercentage());

        item.setQuantity(request.quantity());
        item.setEditorialPriceSnapshot(editorialPrice);
        item.setDiscountPercentage(discount);
        item.setUnitCost(unitCost);
        item.setTotalCost(unitCost == null
                ? null
                : unitCost.multiply(BigDecimal.valueOf(request.quantity())).setScale(2));
        purchaseItemRepository.save(item);
        recalculate(purchase);
        return mapper.toResponse(purchase);
    }

    @Transactional
    public PurchaseResponse removeItem(Long purchaseId, Long itemId) {
        Purchase purchase = requireDraft(purchaseId);
        PurchaseItem item = purchase.getItems().stream()
                .filter(candidate -> candidate.getId().equals(itemId))
                .findFirst().orElseThrow(() -> new BusinessException("Ítem de compra no encontrado"));
        purchase.getItems().remove(item);
        purchaseItemRepository.delete(item);
        recalculate(purchase);
        return mapper.toResponse(purchase);
    }

    @Transactional
    public PurchaseResponse confirm(Long purchaseId) {
        Purchase purchase = requireDraft(purchaseId);
        if (purchase.getItems().isEmpty()) throw new BusinessException("La compra debe contener al menos un libro");

        purchase.getItems().forEach(item -> {
            if (item.getUnitCost() == null) {
                throw new BusinessException(
                        "Falta informar el costo real de '" + item.getBook().getTitle() + "' antes de confirmar la compra"
                );
            }
            purchaseInventoryService.receive(item);
            termService.rememberPurchase(
                    purchase.getSupplier(), item.getBook(), item.getDiscountPercentage(), purchase.getPurchaseDate()
            );
        });

        purchase.setStatus(PurchaseStatus.CONFIRMED);
        recalculate(purchase);
        return mapper.toResponse(purchase);
    }

    private PurchaseResponse loadAndMap(Purchase purchase) {
        return mapper.toResponse(requirePurchase(purchase.getId()));
    }

    private Purchase requireDraft(Long id) {
        Purchase purchase = requirePurchase(id);
        if (purchase.getStatus() != PurchaseStatus.DRAFT) {
            throw new BusinessException("Solo se puede modificar una compra en borrador");
        }
        return purchase;
    }

    private Purchase requirePurchase(Long id) {
        return purchaseRepository.findByIdAndBookstoreId(id, bookstoreContext.getCurrentBookstoreId())
                .orElseThrow(() -> new BusinessException("Compra no encontrada"));
    }

    private void recalculate(Purchase purchase) {
        BigDecimal total = purchase.getItems().stream()
                .map(PurchaseItem::getTotalCost)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2);
        purchase.setTotalAmount(total);
    }

    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
