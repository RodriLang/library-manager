package com.rodrilang.librarymanager.sales.mapper;

import com.rodrilang.librarymanager.auth.models.User;
import com.rodrilang.librarymanager.profitability.dto.response.ProfitabilitySummaryResponse;
import com.rodrilang.librarymanager.sales.dto.response.SaleDetailResponse;
import com.rodrilang.librarymanager.sales.dto.response.SaleItemResponse;
import com.rodrilang.librarymanager.sales.dto.response.SalePaymentResponse;
import com.rodrilang.librarymanager.sales.dto.response.SaleResponse;
import com.rodrilang.librarymanager.sales.model.Sale;
import com.rodrilang.librarymanager.sales.model.SaleItem;
import com.rodrilang.librarymanager.sales.model.SalePayment;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface SaleMapper {

    default SaleResponse toResponse(
            Sale sale,
            int itemCount,
            int totalUnits
    ) {
        return new SaleResponse(
                sale.getId(),
                sale.getStatus(),
                sale.getOrigin(),
                itemCount,
                totalUnits,
                sale.getSubtotal(),
                sale.getDiscountAmount(),
                sale.getTotal(),
                sale.getSoldAt(),
                sale.getCreatedBy().getId(),
                displayName(sale.getCreatedBy())
        );
    }

    default SaleDetailResponse toDetailResponse(
            Sale sale,
            List<SaleItem> items,
            List<SalePayment> payments,
            ProfitabilitySummaryResponse profitability
    ) {
        return new SaleDetailResponse(
                sale.getId(),
                sale.getStatus(),
                sale.getOrigin(),
                sale.getSoldAt(),
                sale.getSubtotal(),
                sale.getDiscountAmount(),
                sale.getTotal(),
                sale.getExternalReference(),
                sale.getNotes(),
                sale.getCreatedBy().getId(),
                displayName(sale.getCreatedBy()),
                sale.getCancelledAt(),
                sale.getCancelledBy() != null ? sale.getCancelledBy().getId() : null,
                displayName(sale.getCancelledBy()),
                sale.getCancellationReason(),
                sale.getCreatedAt(),
                sale.getUpdatedAt(),
                profitability,
                items.stream().map(this::toItemResponse).toList(),
                payments.stream().map(this::toPaymentResponse).toList()
        );
    }

    default SaleItemResponse toItemResponse(SaleItem item) {
        return new SaleItemResponse(
                item.getId(),
                item.getInventory().getId(),
                item.getInventory().getBook().getId(),
                item.getIsbn(),
                item.getDescription(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getSubtotal()
        );
    }

    default SalePaymentResponse toPaymentResponse(SalePayment payment) {
        return new SalePaymentResponse(
                payment.getId(),
                payment.getMethod(),
                payment.getAmount(),
                payment.getReference()
        );
    }

    private String displayName(User user) {
        return user != null ? user.getDisplayName() : null;
    }
}
