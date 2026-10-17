package com.rodrilang.librarymanager.store.repository;

import com.rodrilang.librarymanager.store.model.StoreSection;
import com.rodrilang.librarymanager.store.model.StoreSectionType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface StoreSectionRepository extends JpaRepository<StoreSection, Long> {
    List<StoreSection> findAllByStoreIdOrderByDisplayOrderAscIdAsc(Long storeId);
    Optional<StoreSection> findByStoreIdAndType(Long storeId, StoreSectionType type);
    Optional<StoreSection> findByStoreIdAndSlugIgnoreCase(Long storeId, String slug);
    boolean existsByStoreIdAndSlugIgnoreCase(Long storeId, String slug);
}
