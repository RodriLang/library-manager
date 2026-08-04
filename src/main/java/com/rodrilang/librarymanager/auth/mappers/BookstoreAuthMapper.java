package com.rodrilang.librarymanager.auth.mappers;

import com.rodrilang.librarymanager.auth.dtos.response.BookstoreAuthResponse;
import com.rodrilang.librarymanager.model.Bookstore;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface BookstoreAuthMapper {

    BookstoreAuthResponse toDto(Bookstore bookstore);
}
