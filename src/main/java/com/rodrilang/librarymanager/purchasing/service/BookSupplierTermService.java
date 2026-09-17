package com.rodrilang.librarymanager.purchasing.service;

import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.inventory.cost.service.InventoryCostCalculator;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.purchasing.dto.request.UpsertBookSupplierTermRequest;
import com.rodrilang.librarymanager.purchasing.dto.response.BookSupplierTermResponse;
import com.rodrilang.librarymanager.purchasing.model.BookSupplierTerm;
import com.rodrilang.librarymanager.purchasing.model.Supplier;
import com.rodrilang.librarymanager.purchasing.repository.BookSupplierTermRepository;
import com.rodrilang.librarymanager.repository.BookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
public class BookSupplierTermService {
    private final BookSupplierTermRepository repository;
    private final BookRepository bookRepository;
    private final SupplierService supplierService;
    private final InventoryCostCalculator calculator;
    private final PurchasingMapper mapper;

    @Transactional(readOnly = true)
    public List<BookSupplierTermResponse> findBySupplier(Long supplierId) {
        supplierService.requireSupplier(supplierId);
        return repository.findAllBySupplierIdOrderByBookTitleAsc(supplierId).stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public BookSupplierTermResponse upsert(Long supplierId, UpsertBookSupplierTermRequest request) {
        Supplier supplier = supplierService.requireSupplier(supplierId);
        Book book = bookRepository.findById(request.bookId())
                .orElseThrow(() -> new BusinessException("Libro no encontrado"));
        BookSupplierTerm term = repository.findBySupplierIdAndBookId(supplierId, book.getId())
                .orElseGet(() -> BookSupplierTerm.builder().supplier(supplier).book(book).build());
        term.setDiscountPercentage(calculator.percentage(request.discountPercentage()));
        return mapper.toResponse(repository.save(term));
    }

    @Transactional
    public void rememberPurchase(Supplier supplier, Book book, BigDecimal discount, LocalDate purchaseDate) {
        if (discount == null) return;
        BookSupplierTerm term = repository.findBySupplierIdAndBookId(supplier.getId(), book.getId())
                .orElseGet(() -> BookSupplierTerm.builder().supplier(supplier).book(book).build());
        term.setDiscountPercentage(calculator.percentage(discount));
        term.setLastPurchaseDate(purchaseDate);
        repository.save(term);
    }
}
