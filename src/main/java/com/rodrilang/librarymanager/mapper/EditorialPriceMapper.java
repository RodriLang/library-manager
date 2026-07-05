package com.rodrilang.librarymanager.mapper;

import com.rodrilang.librarymanager.dto.response.EditorialPriceResponse;
import com.rodrilang.librarymanager.model.EditorialPrice;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EditorialPriceMapper {

    EditorialPriceResponse toResponse(EditorialPrice editorialPrice);
}
