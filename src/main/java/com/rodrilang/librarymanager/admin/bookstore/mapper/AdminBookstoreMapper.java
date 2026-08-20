package com.rodrilang.librarymanager.admin.bookstore.mapper;

import com.rodrilang.librarymanager.admin.bookstore.dto.response.AdminBookstoreResponse;
import com.rodrilang.librarymanager.model.Bookstore;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AdminBookstoreMapper {

    AdminBookstoreResponse toDto(Bookstore bookstore);
}