package com.rodrilang.librarymanager.mapper;

import com.rodrilang.librarymanager.dto.request.BookRequest;
import com.rodrilang.librarymanager.dto.request.UpdateBookRequest;
import com.rodrilang.librarymanager.dto.response.BookDetailResponse;
import com.rodrilang.librarymanager.dto.response.BookSummaryResponse;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.EditorialPrice;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

@Mapper(componentModel = "spring", uses = {AuthorMapper.class, PublisherMapper.class, EditorialPriceMapper.class})
public interface BookMapper {

    @Mapping(target = "id", source = "book.id")
    @Mapping(target = "source", source = "book.source")
    @Mapping(target = "active", source = "book.active")
    @Mapping(target = "createdAt", source = "book.createdAt")
    @Mapping(target = "updatedAt", source = "book.updatedAt")
    @Mapping(target = "editorialPrice", source = "editorialPrice")
    BookDetailResponse toDetailResponse(Book book, EditorialPrice editorialPrice);

    @Mapping(target = "id", source = "book.id")
    @Mapping(target = "publisherName", source = "book.publisher.name")
    @Mapping(target = "editorialPrice", source = "editorialPrice")
    BookSummaryResponse toSummaryResponse(Book book, EditorialPrice editorialPrice);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "publisher", ignore = true)
    @Mapping(target = "authors", ignore = true)
    @Mapping(target = "coverUrl", ignore = true)
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "titleSort", ignore = true)
    @Mapping(target = "createdByBookstore", ignore = true)
    @Mapping(target = "catalogStatus", ignore = true)
    Book toEntity(BookRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "isbn", ignore = true)
    @Mapping(target = "publisher", ignore = true)
    @Mapping(target = "authors", ignore = true)
    @Mapping(target = "source", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "titleSort", ignore = true)
    @Mapping(target = "createdByBookstore", ignore = true)
    @Mapping(target = "catalogStatus", ignore = true)
    void updateEntity(UpdateBookRequest request, @MappingTarget Book inventory);
}
