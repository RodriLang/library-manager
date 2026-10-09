package com.rodrilang.librarymanager.store.repository;
import com.rodrilang.librarymanager.store.model.StorePublication;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
public interface StorePublicationRepository extends JpaRepository<StorePublication, Long>, JpaSpecificationExecutor<StorePublication> {
    Optional<StorePublication> findByStoreIdAndInventoryId(Long storeId, Long inventoryId);
    List<StorePublication> findAllByStoreIdAndInventoryIdIn(Long storeId, Collection<Long> inventoryIds);
    @EntityGraph(attributePaths = {"inventory", "inventory.book", "inventory.book.authors", "inventory.book.publisher"})
    @Query("select p from StorePublication p where p.store.id = :storeId and p.inventory.id = :inventoryId")
    Optional<StorePublication> findDetailed(@Param("storeId") Long storeId, @Param("inventoryId") Long inventoryId);
    @EntityGraph(attributePaths = {"inventory", "inventory.book", "inventory.book.authors", "inventory.book.publisher"})
    List<StorePublication> findAllByIdIn(Collection<Long> ids);
}

