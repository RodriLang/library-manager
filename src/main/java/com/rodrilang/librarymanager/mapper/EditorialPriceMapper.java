package com.rodrilang.librarymanager.mapper;

import com.rodrilang.librarymanager.dto.response.EditorialPriceResponse;
import com.rodrilang.librarymanager.model.EditorialPrice;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface EditorialPriceMapper {

    default EditorialPriceResponse toResponse(EditorialPrice editorialPrice) {
        if (editorialPrice == null) {
            return EditorialPriceResponse.empty();
        }

        return new EditorialPriceResponse(
                editorialPrice.getPrice(),
                editorialPrice.getValidFrom()
        );
    }
}
