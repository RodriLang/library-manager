package com.rodrilang.librarymanager.purchasing.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.purchasing.dto.request.CreateSupplierRequest;
import com.rodrilang.librarymanager.purchasing.dto.request.UpdateSupplierRequest;
import com.rodrilang.librarymanager.purchasing.dto.response.SupplierResponse;
import com.rodrilang.librarymanager.purchasing.model.Supplier;
import com.rodrilang.librarymanager.purchasing.repository.SupplierRepository;
import com.rodrilang.librarymanager.repository.BookstoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SupplierService {
    private final SupplierRepository supplierRepository;
    private final BookstoreRepository bookstoreRepository;
    private final BookstoreContext bookstoreContext;
    private final PurchasingMapper mapper;

    @Transactional(readOnly = true)
    public List<SupplierResponse> findAll() {
        return supplierRepository.findAllByBookstoreIdOrderByNameAsc(bookstoreContext.getCurrentBookstoreId())
                .stream().map(mapper::toResponse).toList();
    }

    @Transactional
    public SupplierResponse create(CreateSupplierRequest request) {
        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();
        String name = normalize(request.name());
        if (supplierRepository.existsByBookstoreIdAndNameIgnoreCase(bookstoreId, name)) {
            throw new BusinessException("Ya existe un proveedor con ese nombre");
        }
        Bookstore bookstore = bookstoreRepository.findById(bookstoreId)
                .orElseThrow(() -> new BusinessException("Librería no encontrada"));
        Supplier supplier = supplierRepository.save(Supplier.builder()
                .bookstore(bookstore).name(name).taxId(clean(request.taxId())).email(clean(request.email()))
                .phone(clean(request.phone())).notes(clean(request.notes())).active(true).build());
        return mapper.toResponse(supplier);
    }

    @Transactional
    public SupplierResponse update(Long supplierId, UpdateSupplierRequest request) {
        Supplier supplier = requireSupplier(supplierId);
        String name = normalize(request.name());
        if (!supplier.getName().equalsIgnoreCase(name)
                && supplierRepository.existsByBookstoreIdAndNameIgnoreCase(supplier.getBookstore().getId(), name)) {
            throw new BusinessException("Ya existe un proveedor con ese nombre");
        }
        supplier.setName(name);
        supplier.setTaxId(clean(request.taxId()));
        supplier.setEmail(clean(request.email()));
        supplier.setPhone(clean(request.phone()));
        supplier.setNotes(clean(request.notes()));
        if (request.active() != null) supplier.setActive(request.active());
        return mapper.toResponse(supplier);
    }

    public Supplier requireSupplier(Long supplierId) {
        return supplierRepository.findByIdAndBookstoreId(supplierId, bookstoreContext.getCurrentBookstoreId())
                .orElseThrow(() -> new BusinessException("Proveedor no encontrado"));
    }

    private String normalize(String value) { return value.trim(); }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
