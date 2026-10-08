package com.rodrilang.librarymanager.store.payment.controller;

import com.rodrilang.librarymanager.store.payment.service.StoreMercadoPagoPaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/storefront/payments/mercado-pago")
@RequiredArgsConstructor
public class StoreMercadoPagoWebhookController {
    private final StoreMercadoPagoPaymentService paymentService;

    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(
            @RequestParam(name = "data.id", required = false) String dataId,
            @RequestParam(name = "type", required = false) String type,
            @RequestHeader(name = "x-request-id", required = false) String requestId,
            @RequestHeader(name = "x-signature", required = false) String signature
    ) {
        if (dataId == null || dataId.isBlank() || (type != null && !"order".equalsIgnoreCase(type))) {
            return ResponseEntity.ok().build();
        }
        boolean valid = paymentService.processWebhook(dataId, requestId, signature);
        return valid ? ResponseEntity.ok().build() : ResponseEntity.status(401).build();
    }
}
