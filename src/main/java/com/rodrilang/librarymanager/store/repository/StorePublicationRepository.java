package com.rodrilang.librarymanager.store.repository;
import com.rodrilang.librarymanager.store.model.StorePublication;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.time.Instant;
import org.springframework.data.domain.Pageable;
public interface StorePublicationRepository extends JpaRepository<StorePublication, Long>, JpaSpecificationExecutor<StorePublication> {
    Optional<StorePublication> findByStoreIdAndInventoryId(Long storeId, Long inventoryId);
    List<StorePublication> findAllByStoreIdAndInventoryIdIn(Long storeId, Collection<Long> inventoryIds);
    @EntityGraph(attributePaths = {"inventory", "inventory.book", "inventory.book.authors", "inventory.book.publisher"})
    @Query("select p from StorePublication p where p.store.id = :storeId and p.inventory.id = :inventoryId")
    Optional<StorePublication> findDetailed(@Param("storeId") Long storeId, @Param("inventoryId") Long inventoryId);
    @EntityGraph(attributePaths = {"inventory", "inventory.book", "inventory.book.authors", "inventory.book.publisher"})
    List<StorePublication> findAllByIdIn(Collection<Long> ids);
    @EntityGraph(attributePaths = {"inventory", "inventory.book", "inventory.book.authors", "inventory.book.publisher"})
    @Query("select p from StorePublication p where p.store.id = :storeId and p.published = true and p.featured = true and p.inventory.active = true and p.inventory.book.active = true order by case when p.featuredOrder is null then 1 else 0 end, p.featuredOrder asc, p.publishedAt desc, p.id desc")
    List<StorePublication> findFeaturedForStore(@Param("storeId") Long storeId, Pageable pageable);

    @EntityGraph(attributePaths = {"inventory", "inventory.book", "inventory.book.authors", "inventory.book.publisher"})
    @Query("select p from StorePublication p where p.store.id = :storeId and p.published = true and coalesce(p.firstPublishedAt, p.publishedAt) >= :after and p.inventory.active = true and p.inventory.book.active = true order by coalesce(p.firstPublishedAt, p.publishedAt) desc, p.id desc")
    List<StorePublication> findNewArrivalsForStore(@Param("storeId") Long storeId, @Param("after") Instant after, Pageable pageable);

    @EntityGraph(attributePaths = {"inventory", "inventory.book", "inventory.book.authors", "inventory.book.publisher"})
    @Query("select p from StorePublication p where p.store.id = :storeId and p.inventory.id in :inventoryIds and p.published = true and p.inventory.active = true and p.inventory.book.active = true")
    List<StorePublication> findPublishedByStoreAndInventoryIds(@Param("storeId") Long storeId, @Param("inventoryIds") Collection<Long> inventoryIds);

}

