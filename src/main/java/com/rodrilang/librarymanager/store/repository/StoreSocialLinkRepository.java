package com.rodrilang.librarymanager.store.repository;

import com.rodrilang.librarymanager.store.model.StoreSocialLink;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;

public interface StoreSocialLinkRepository extends JpaRepository<StoreSocialLink, Long> {
    List<StoreSocialLink> findAllByStoreIdOrderByDisplayOrderAscIdAsc(Long storeId);
    void deleteAllByStoreId(Long storeId);
}
