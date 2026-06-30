package com.rodrilang.librarymanager.mapper;

import com.rodrilang.librarymanager.dto.request.BookRequest;
import com.rodrilang.librarymanager.dto.response.BookDetailResponse;
import com.rodrilang.librarymanager.dto.response.BookSummaryResponse;
import com.rodrilang.librarymanager.model.Book;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", uses = {AuthorMapper.class, PublisherMapper.class})
public interface BookMapper {

    BookDetailResponse toDetailResponse(Book entity);

    @Mapping(target = "publisherName", source = "publisher.name")
    BookSummaryResponse toSummaryResponse(Book entity);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "publisher", ignore = true)
    @Mapping(target = "authors", ignore = true)
    Book toEntity(BookRequest request);
}
