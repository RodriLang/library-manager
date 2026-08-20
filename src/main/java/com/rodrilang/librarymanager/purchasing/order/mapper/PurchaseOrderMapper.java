package com.rodrilang.librarymanager.purchasing.order.mapper;

import com.rodrilang.librarymanager.purchasing.order.dto.response.PurchaseOrderItemResponse;
import com.rodrilang.librarymanager.purchasing.order.model.PurchaseOrderItem;
import org.mapstruct.Mapper;

import java.math.BigDecimal;

@Mapper(componentModel = "spring")
public interface PurchaseOrderMapper {

    default PurchaseOrderItemResponse toItemResponse(PurchaseOrderItem item) {

        BigDecimal subtotal = item.getUnitPrice() != null
                        ? item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity()))
                        : null;

        int requirementQuantity = item.getRequirementQuantity() != null
                        ? item.getRequirementQuantity()
                        : 0;

        int additionalQuantity = item.getQuantity() - requirementQuantity;

        return new PurchaseOrderItemResponse(
                item.getId(),
                item.getBook().getId(),
                item.getBook().getPreferredIsbn(),
                item.getBook().getTitle(),
                item.getBook().getCoverUrl(),

                item.getRequirement() != null
                        ? item.getRequirement().getId()
                        : null,

                item.getQuantity(),
                requirementQuantity,
                additionalQuantity,

                item.getUnitPrice(),
                subtotal
        );
    }
}