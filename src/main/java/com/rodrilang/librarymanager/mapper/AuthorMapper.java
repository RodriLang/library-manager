package com.rodrilang.librarymanager.mapper;

import com.rodrilang.librarymanager.dto.request.AuthorRequest;
import com.rodrilang.librarymanager.dto.response.AuthorResponse;
import com.rodrilang.librarymanager.model.Author;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface AuthorMapper {

    AuthorResponse toResponse(Author entity);

    Author toEntity(AuthorRequest request);
}
