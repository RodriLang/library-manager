package com.rodrilang.librarymanager.purchasing.payment.controller;

import com.rodrilang.librarymanager.purchasing.dto.response.PurchaseResponse;
import com.rodrilang.librarymanager.purchasing.payment.dto.request.CancelPurchasePaymentRequest;
import com.rodrilang.librarymanager.purchasing.payment.dto.request.CreatePurchasePaymentRequest;
import com.rodrilang.librarymanager.purchasing.payment.service.PurchasePaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/purchases/{purchaseId}/payments")
@RequiredArgsConstructor
@Tag(name = "Pagos de compras", description = "Pagos realizados a proveedores por compras confirmadas")
public class PurchasePaymentController {

    private final PurchasePaymentService paymentService;

    @PostMapping
    public ResponseEntity<PurchaseResponse> addPayment(
            @PathVariable Long purchaseId,
            @Valid @RequestBody CreatePurchasePaymentRequest request
    ) {
        return ResponseEntity.ok(paymentService.addPayment(purchaseId, request));
    }

    @PostMapping("/{paymentId}/cancel")
    public ResponseEntity<PurchaseResponse> cancelPayment(
            @PathVariable Long purchaseId,
            @PathVariable Long paymentId,
            @Valid @RequestBody CancelPurchasePaymentRequest request
    ) {
        return ResponseEntity.ok(paymentService.cancelPayment(purchaseId, paymentId, request));
    }
}
