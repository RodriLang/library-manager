package com.rodrilang.librarymanager.admin.integration.mercadopago.repository;

import com.rodrilang.librarymanager.admin.integration.mercadopago.model.MercadoPagoPlatformConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MercadoPagoPlatformConfigRepository extends JpaRepository<MercadoPagoPlatformConfig, Long> {
}
