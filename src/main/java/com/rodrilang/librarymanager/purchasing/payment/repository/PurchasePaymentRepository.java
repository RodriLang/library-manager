package com.rodrilang.librarymanager.purchasing.payment.repository;

import com.rodrilang.librarymanager.purchasing.payment.model.PurchasePayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PurchasePaymentRepository extends JpaRepository<PurchasePayment, Long> {

    Optional<PurchasePayment> findByIdAndPurchaseId(Long id, Long purchaseId);
}
