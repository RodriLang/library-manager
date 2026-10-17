package com.rodrilang.librarymanager.store.repository;

import com.rodrilang.librarymanager.store.model.StoreSectionItem;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface StoreSectionItemRepository extends JpaRepository<StoreSectionItem, Long> {
    @EntityGraph(attributePaths = {"inventory", "inventory.book", "inventory.book.authors", "inventory.book.publisher"})
    List<StoreSectionItem> findAllBySectionIdOrderByDisplayOrderAscIdAsc(Long sectionId);
    long countBySectionId(Long sectionId);
    void deleteAllBySectionId(Long sectionId);

    @Query("select i.inventory.id from StoreSectionItem i where i.section.id = :sectionId order by i.displayOrder asc, i.id asc")
    List<Long> findInventoryIdsBySectionId(@Param("sectionId") Long sectionId);
}
