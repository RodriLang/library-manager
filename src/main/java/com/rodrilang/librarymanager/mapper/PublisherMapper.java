package com.rodrilang.librarymanager.mapper;

import com.rodrilang.librarymanager.dto.request.PublisherRequest;
import com.rodrilang.librarymanager.dto.response.PublisherResponse;
import com.rodrilang.librarymanager.model.Publisher;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PublisherMapper {

    PublisherResponse toResponse(Publisher entity);

    Publisher toEntity(PublisherRequest request);
}
