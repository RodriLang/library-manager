package com.rodrilang.librarymanager.sales.repository;

import com.rodrilang.librarymanager.sales.model.SalePayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SalePaymentRepository extends JpaRepository<SalePayment, Long> {

    List<SalePayment> findAllBySaleIdOrderByIdAsc(Long saleId);
}
