package com.rodrilang.librarymanager.purchasing.requirement.mapper;

import com.rodrilang.librarymanager.model.Inventory;
import com.rodrilang.librarymanager.purchasing.requirement.dto.response.PurchaseRequirementInventoryResponse;
import com.rodrilang.librarymanager.purchasing.requirement.dto.response.PurchaseRequirementProviderResponse;
import com.rodrilang.librarymanager.purchasing.requirement.dto.response.PurchaseRequirementReasonResponse;
import com.rodrilang.librarymanager.purchasing.requirement.dto.response.PurchaseRequirementResponse;
import com.rodrilang.librarymanager.purchasing.requirement.dto.response.PurchaseRequirementSummaryResponse;
import com.rodrilang.librarymanager.purchasing.requirement.model.PurchaseRequirement;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PurchaseRequirementMapper {

    @Mapping(target = "bookId", source = "book.id")
    @Mapping(target = "isbn", source = "book.preferredIsbn")
    @Mapping(target = "title", source = "book.title")
    @Mapping(target = "coverUrl", source = "book.coverUrl")
    @Mapping(target = "preferredProviderId", source = "preferredProvider.id")
    @Mapping(target = "preferredProviderName", source = "preferredProvider.name")
    PurchaseRequirementResponse toResponse(
            PurchaseRequirement requirement
    );

    default PurchaseRequirementSummaryResponse toSummaryResponse(
            PurchaseRequirement requirement,
            Inventory inventory,
            List<PurchaseRequirementReasonResponse> reasons,
            List<PurchaseRequirementProviderResponse> availableProviders,
            int orderedQuantity
    ) {

        var book = requirement.getBook();
        var provider = requirement.getPreferredProvider();

        PurchaseRequirementInventoryResponse inventoryResponse =
                inventory != null
                        ? new PurchaseRequirementInventoryResponse(
                        inventory.getId(),
                        true,
                        inventory.getStock(),
                        inventory.getMinimumStock()
                )
                        : new PurchaseRequirementInventoryResponse(
                        null,
                        false,
                        null,
                        null
                );

        int remainingQuantity =
                Math.max(
                        requirement.getQuantity() - orderedQuantity,
                        0
                );

        return new PurchaseRequirementSummaryResponse(
                requirement.getId(),
                book.getId(),
                book.getPreferredIsbn(),
                book.getTitle(),
                book.getCoverUrl(),

                requirement.getQuantity(),
                orderedQuantity,
                remainingQuantity,

                inventoryResponse,

                provider != null
                        ? provider.getId()
                        : null,

                provider != null
                        ? provider.getName()
                        : null,

                availableProviders,

                reasons,

                requirement.getStatus()
        );
    }
}