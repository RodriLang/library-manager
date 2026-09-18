package com.rodrilang.librarymanager.purchasing.payment.service;

import com.rodrilang.librarymanager.purchasing.payment.dto.response.PurchasePaymentResponse;
import com.rodrilang.librarymanager.purchasing.payment.model.PurchasePayment;
import org.springframework.stereotype.Component;

@Component
public class PurchasePaymentMapper {

    public PurchasePaymentResponse toResponse(PurchasePayment payment) {
        return new PurchasePaymentResponse(
                payment.getId(),
                payment.getPaidAt(),
                payment.getMethod(),
                payment.getAmount(),
                payment.getReference(),
                payment.getNotes(),
                payment.isActive(),
                payment.getCancelledAt(),
                payment.getCancellationReason(),
                payment.getCreatedAt()
        );
    }
}
