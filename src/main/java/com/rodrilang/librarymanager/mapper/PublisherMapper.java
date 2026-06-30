package com.rodrilang.librarymanager.mapper;

import com.rodrilang.librarymanager.dto.request.PublisherRequest;
import com.rodrilang.librarymanager.dto.response.PublisherResponse;
import com.rodrilang.librarymanager.model.Publisher;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PublisherMapper {

    PublisherResponse toResponse(Publisher entity);

    @Mapping(target = "id", ignore = true)
    Publisher toEntity(PublisherRequest request);
}
