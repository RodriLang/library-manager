package com.rodrilang.librarymanager.inventory.movement.service.impl;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.inventory.movement.dto.InventoryMovementFilter;
import com.rodrilang.librarymanager.inventory.movement.dto.InventoryMovementResponse;
import com.rodrilang.librarymanager.inventory.movement.mapper.InventoryMovementMapper;
import com.rodrilang.librarymanager.inventory.movement.repository.InventoryMovementRepository;
import com.rodrilang.librarymanager.inventory.movement.repository.InventoryMovementSpecifications;
import com.rodrilang.librarymanager.inventory.movement.service.InventoryMovementQueryService;
import com.rodrilang.librarymanager.model.InventoryMovement;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class InventoryMovementQueryServiceImpl implements InventoryMovementQueryService {

    private final InventoryMovementRepository inventoryMovementRepository;
    private final InventoryMovementMapper inventoryMovementMapper;
    private final BookstoreContext bookstoreContext;

    @Override
    public Page<InventoryMovementResponse> findAll(
            InventoryMovementFilter filter,
            Pageable pageable
    ) {

        Long bookstoreId = bookstoreContext.getCurrentBookstoreId();

        Specification<InventoryMovement> specification = Specification.allOf(
                InventoryMovementSpecifications.bookstoreId(bookstoreId),
                InventoryMovementSpecifications.search(filter.query()),
                InventoryMovementSpecifications.inventoryId(filter.inventoryId()),
                InventoryMovementSpecifications.type(filter.type()),
                InventoryMovementSpecifications.source(filter.source()),
                InventoryMovementSpecifications.from(filter.from()),
                InventoryMovementSpecifications.to(filter.to())
        );

        return inventoryMovementRepository
                .findAll(specification, pageable)
                .map(inventoryMovementMapper::toResponse);
    }
}