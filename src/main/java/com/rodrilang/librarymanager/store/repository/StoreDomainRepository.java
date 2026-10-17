package com.rodrilang.librarymanager.store.repository;
import com.rodrilang.librarymanager.store.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
public interface StoreDomainRepository extends JpaRepository<StoreDomain, Long> {
    Optional<StoreDomain> findByHostnameIgnoreCaseAndStatus(String hostname, StoreDomainStatus status);
    Optional<StoreDomain> findByHostnameIgnoreCase(String hostname);
    List<StoreDomain> findAllByStoreIdOrderByIdAsc(Long storeId);
    Optional<StoreDomain> findByStoreIdAndType(Long storeId, StoreDomainType type);
}
