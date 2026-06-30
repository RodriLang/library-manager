package com.rodrilang.librarymanager.mapper;

import com.rodrilang.librarymanager.dto.request.BookRequest;
import com.rodrilang.librarymanager.dto.response.BookDetailResponse;
import com.rodrilang.librarymanager.dto.response.BookSummaryResponse;
import com.rodrilang.librarymanager.model.Book;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring", uses = {AuthorMapper.class, PublisherMapper.class})
public interface BookMapper {

    BookDetailResponse toDetailResponse(Book entity);

    BookSummaryResponse toSummaryResponse(Book entity);

    Book toEntity(BookRequest request);
}
