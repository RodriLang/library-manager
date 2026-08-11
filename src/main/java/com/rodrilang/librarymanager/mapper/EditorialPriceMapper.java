package com.rodrilang.librarymanager.mapper;

import com.rodrilang.librarymanager.dto.response.EditorialPriceResponse;
import com.rodrilang.librarymanager.model.EditorialPrice;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface EditorialPriceMapper {

    @Mapping(target = "providerId", source = "provider.id")
    @Mapping(target = "providerName", source = "provider.name")
    @Mapping(target = "providerCode", source = "provider.code")
    EditorialPriceResponse toResponse(EditorialPrice editorialPrice);
}
