package com.rodrilang.librarymanager.purchasing.payment.service;

import com.rodrilang.librarymanager.bookstore.BookstoreContext;
import com.rodrilang.librarymanager.exception.BusinessException;
import com.rodrilang.librarymanager.purchasing.dto.response.PurchaseResponse;
import com.rodrilang.librarymanager.purchasing.model.Purchase;
import com.rodrilang.librarymanager.purchasing.model.PurchaseStatus;
import com.rodrilang.librarymanager.purchasing.payment.dto.request.CancelPurchasePaymentRequest;
import com.rodrilang.librarymanager.purchasing.payment.dto.request.CreatePurchasePaymentRequest;
import com.rodrilang.librarymanager.purchasing.payment.model.PurchasePayment;
import com.rodrilang.librarymanager.purchasing.payment.repository.PurchasePaymentRepository;
import com.rodrilang.librarymanager.purchasing.repository.PurchaseRepository;
import com.rodrilang.librarymanager.purchasing.service.PurchasingMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class PurchasePaymentService {

    private final PurchaseRepository purchaseRepository;
    private final PurchasePaymentRepository paymentRepository;
    private final BookstoreContext bookstoreContext;
    private final PurchasingMapper mapper;

    @Transactional
    public PurchaseResponse addPayment(Long purchaseId, CreatePurchasePaymentRequest request) {
        Purchase purchase = requireConfirmedForUpdate(purchaseId);
        BigDecimal amount = money(request.amount());
        BigDecimal paidAmount = purchase.getPaidAmount();
        BigDecimal pendingAmount = purchase.getTotalAmount().subtract(paidAmount).setScale(2);

        if (amount.compareTo(pendingAmount) > 0) {
            throw new BusinessException(
                    "El pago supera el saldo pendiente de la compra ($" + pendingAmount.toPlainString() + ")"
            );
        }

        PurchasePayment payment = paymentRepository.save(PurchasePayment.builder()
                .purchase(purchase)
                .paidAt(request.paidAt() == null ? Instant.now() : request.paidAt())
                .method(request.method())
                .amount(amount)
                .reference(clean(request.reference()))
                .notes(clean(request.notes()))
                .build());

        purchase.getPayments().add(payment);
        return mapper.toResponse(purchase);
    }

    @Transactional
    public PurchaseResponse cancelPayment(
            Long purchaseId,
            Long paymentId,
            CancelPurchasePaymentRequest request
    ) {
        Purchase purchase = requireConfirmedForUpdate(purchaseId);
        PurchasePayment payment = paymentRepository.findByIdAndPurchaseId(paymentId, purchaseId)
                .orElseThrow(() -> new BusinessException("Pago de compra no encontrado"));

        if (!payment.isActive()) {
            throw new BusinessException("El pago ya fue anulado");
        }

        payment.setCancelledAt(Instant.now());
        payment.setCancellationReason(request.reason().trim());
        paymentRepository.save(payment);

        return mapper.toResponse(purchase);
    }

    private Purchase requireConfirmedForUpdate(Long purchaseId) {
        Purchase purchase = purchaseRepository.findByIdAndBookstoreIdForUpdate(
                        purchaseId,
                        bookstoreContext.getCurrentBookstoreId()
                )
                .orElseThrow(() -> new BusinessException("Compra no encontrada"));

        if (purchase.getStatus() != PurchaseStatus.CONFIRMED) {
            throw new BusinessException("Solo se pueden registrar pagos sobre una compra confirmada");
        }

        return purchase;
    }

    private BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
