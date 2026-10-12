package com.rodrilang.librarymanager.shipping.returns.repository;
import com.rodrilang.librarymanager.shipping.returns.model.StoreReturn;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface StoreReturnRepository extends JpaRepository<StoreReturn,Long> {
    List<StoreReturn> findAllByOrderIdAndBookstoreIdOrderByCreatedAtDesc(Long orderId, Long bookstoreId);
    Optional<StoreReturn> findByIdAndOrderIdAndBookstoreId(Long id, Long orderId, Long bookstoreId);
}
