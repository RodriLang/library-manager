package com.rodrilang.librarymanager.mapper;

import com.rodrilang.librarymanager.dto.response.EditorialPriceResponse;
import com.rodrilang.librarymanager.editorialprice.model.EffectiveEditorialPrice;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EditorialPriceMapper {

    EditorialPriceResponse toResponse(EffectiveEditorialPrice editorialPrice);
}