package com.rodrilang.librarymanager.mapper;

import com.rodrilang.librarymanager.dto.request.UpdateInventoryRequest;
import com.rodrilang.librarymanager.dto.response.InventoryDetailResponse;
import com.rodrilang.librarymanager.dto.response.InventorySummaryResponse;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.Inventory;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(componentModel = "spring", uses = {BookMapper.class})
public interface InventoryMapper {

    InventoryDetailResponse toDetailResponse(Inventory inventory);

    @Mapping(target = "bookId", source = "book.id")
    @Mapping(target = "isbn", source = "book.isbn")
    @Mapping(target = "title", source = "book.title")
    @Mapping(target = "publisherName", source = "book.publisher.name")
    @Mapping(target = "thumbnailUrl", source = "book.thumbnailUrl")
    @Mapping(target = "authorNames", expression = "java(toAuthorNames(inventory))")
    InventorySummaryResponse toSummaryResponse(Inventory inventory);

    default List<String> toAuthorNames(Inventory inventory) {
        if (inventory.getBook() == null || inventory.getBook().getAuthors() == null) {
            return List.of();
        }

        return inventory.getBook().getAuthors()
                .stream()
                .map(Author::getName)
                .sorted()
                .toList();
    }

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(UpdateInventoryRequest request, @MappingTarget Inventory inventory);
}