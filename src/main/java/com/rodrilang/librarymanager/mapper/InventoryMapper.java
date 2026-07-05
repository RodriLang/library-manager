package com.rodrilang.librarymanager.mapper;

import com.rodrilang.librarymanager.dto.request.UpdateInventoryRequest;
import com.rodrilang.librarymanager.dto.response.BookDetailResponse;
import com.rodrilang.librarymanager.dto.response.InventoryDetailResponse;
import com.rodrilang.librarymanager.dto.response.InventorySummaryResponse;
import com.rodrilang.librarymanager.model.Author;
import com.rodrilang.librarymanager.model.EditorialPrice;
import com.rodrilang.librarymanager.model.Inventory;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

@Mapper(componentModel = "spring", uses = {BookMapper.class})
public abstract class InventoryMapper {

    protected BookMapper bookMapper;

    @Autowired
    public void setBookMapper(BookMapper bookMapper) {
        this.bookMapper = bookMapper;
    }

    @Mapping(target = "id", source = "inventory.id")
    @Mapping(target = "active", source = "inventory.active")
    @Mapping(target = "createdAt", source = "inventory.createdAt")
    @Mapping(target = "updatedAt", source = "inventory.updatedAt")
    @Mapping(target = "book", expression = "java(toBookDetailResponse(inventory, editorialPrice))")
    public abstract InventoryDetailResponse toDetailResponse(Inventory inventory, EditorialPrice editorialPrice);

    protected BookDetailResponse toBookDetailResponse(Inventory inventory, EditorialPrice editorialPrice) {
        if (inventory == null || inventory.getBook() == null) {
            return null;
        }

        return bookMapper.toDetailResponse(inventory.getBook(), editorialPrice);
    }

    @Mapping(target = "id", source = "inventory.id")
    @Mapping(target = "bookId", source = "inventory.book.id")
    @Mapping(target = "isbn", source = "inventory.book.isbn")
    @Mapping(target = "title", source = "inventory.book.title")
    @Mapping(target = "publisherName", source = "inventory.book.publisher.name")
    @Mapping(target = "coverUrl", source = "inventory.book.coverUrl")
    @Mapping(target = "active", source = "inventory.active")
    @Mapping(target = "editorialPrice", source = "editorialPrice.price")
    @Mapping(target = "editorialPriceValidFrom", source = "editorialPrice.validFrom")
    @Mapping(target = "authorNames", expression = "java(toAuthorNames(inventory))")
    public abstract InventorySummaryResponse toSummaryResponse(Inventory inventory, EditorialPrice editorialPrice);

    protected List<String> toAuthorNames(Inventory inventory) {
        if (inventory == null || inventory.getBook() == null || inventory.getBook().getAuthors() == null) {
            return List.of();
        }

        return inventory.getBook().getAuthors()
                .stream()
                .map(Author::getName)
                .sorted()
                .toList();
    }

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "book", ignore = true)
    @Mapping(target = "stock", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "bookstore", ignore = true)
    public abstract void updateEntity(UpdateInventoryRequest request, @MappingTarget Inventory inventory);
}