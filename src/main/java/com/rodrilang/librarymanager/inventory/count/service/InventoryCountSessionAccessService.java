package com.rodrilang.librarymanager.inventory.count.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.ResourceNotFoundException;
import com.rodrilang.librarymanager.inventory.count.model.InventoryCountSession;
import com.rodrilang.librarymanager.inventory.count.repository.InventoryCountSessionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InventoryCountSessionAccessService {

    private final InventoryCountSessionRepository repository;
    private final BookstoreContext bookstoreContext;

    public InventoryCountSession require(Long sessionId) {
        return repository.findByIdAndBookstoreId(sessionId, bookstoreContext.getCurrentBookstoreId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el conteo de inventario"));
    }

    public InventoryCountSession requireForUpdate(Long sessionId) {
        return repository.findByIdAndBookstoreIdForUpdate(sessionId, bookstoreContext.getCurrentBookstoreId())
                .orElseThrow(() -> new ResourceNotFoundException("No se encontró el conteo de inventario"));
    }
}
