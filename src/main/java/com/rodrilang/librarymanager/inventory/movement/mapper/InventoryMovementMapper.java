package com.rodrilang.librarymanager.inventory.movement.mapper;

import com.rodrilang.librarymanager.inventory.movement.dto.InventoryMovementResponse;
import com.rodrilang.librarymanager.model.InventoryMovement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface InventoryMovementMapper {

    @Mapping(target = "inventoryId", source = "inventory.id")
    @Mapping(target = "bookId", source = "inventory.book.id")
    @Mapping(target = "isbn", source = "inventory.book.preferredIsbn")
    @Mapping(target = "title", source = "inventory.book.title")
    @Mapping(target = "coverUrl", source = "inventory.book.coverUrl")
    @Mapping(target = "consignmentProviderId", source = "consignmentProvider.id")
    @Mapping(target = "consignmentProviderName", source = "consignmentProvider.name")
    InventoryMovementResponse toResponse(InventoryMovement movement);
}