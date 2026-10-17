package com.rodrilang.librarymanager.store.repository;

import com.rodrilang.librarymanager.store.model.StoreHeroItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface StoreHeroItemRepository extends JpaRepository<StoreHeroItem, Long> {
    List<StoreHeroItem> findAllByStoreIdOrderByDisplayOrderAscIdAsc(Long storeId);
    void deleteAllByStoreId(Long storeId);
}
