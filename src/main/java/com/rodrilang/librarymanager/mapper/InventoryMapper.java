package com.rodrilang.librarymanager.mapper;

import com.rodrilang.librarymanager.dto.response.InventoryDetailResponse;
import com.rodrilang.librarymanager.dto.response.InventorySummaryResponse;
import com.rodrilang.librarymanager.model.Inventory;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {AuthorMapper.class})
public interface InventoryMapper {

    @Mapping(target = "bookId", source = "book.id")
    @Mapping(target = "isbn", source = "book.isbn")
    @Mapping(target = "title", source = "book.title")
    @Mapping(target = "publisherName", source = "book.publisher.name")
    @Mapping(target = "authors", source = "book.authors")
    InventoryDetailResponse toDetailResponse(Inventory inventory);

    @Mapping(target = "bookId", source = "book.id")
    @Mapping(target = "isbn", source = "book.isbn")
    @Mapping(target = "title", source = "book.title")
    InventorySummaryResponse toSummaryResponse(Inventory inventory);

}