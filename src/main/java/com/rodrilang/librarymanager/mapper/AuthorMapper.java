package com.rodrilang.librarymanager.mapper;

import com.rodrilang.librarymanager.dto.request.AuthorRequest;
import com.rodrilang.librarymanager.dto.response.AuthorResponse;
import com.rodrilang.librarymanager.model.Author;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AuthorMapper {

    AuthorResponse toResponse(Author entity);

    @Mapping(target = "id", ignore = true)
    Author toEntity(AuthorRequest request);
}
