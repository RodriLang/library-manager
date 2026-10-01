package com.rodrilang.librarymanager.inventory.pricing.repository;

import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface InventoryPriceRepository extends JpaRepository<InventoryPrice, Long> {

    Optional<InventoryPrice> findByInventoryIdAndEffectiveFrom(Long inventoryId, LocalDate effectiveFrom);

    @Query("""
            select p
            from InventoryPrice p
            where p.inventory.id = :inventoryId
              and p.effectiveFrom <= :date
            order by p.effectiveFrom desc, p.id desc
            """)
    List<InventoryPrice> findCurrentCandidates(
            @Param("inventoryId") Long inventoryId,
            @Param("date") LocalDate date
    );

    @Query("""
            select p
            from InventoryPrice p
            where p.inventory.id in :inventoryIds
              and p.effectiveFrom <= :date
            order by p.inventory.id asc, p.effectiveFrom desc, p.id desc
            """)
    List<InventoryPrice> findCurrentCandidatesForInventoryIds(
            @Param("inventoryIds") Collection<Long> inventoryIds,
            @Param("date") LocalDate date
    );

    @Query("""
            select p
            from InventoryPrice p
            where p.inventory.id = :inventoryId
              and p.effectiveFrom > :date
            order by p.effectiveFrom asc, p.id asc
            """)
    List<InventoryPrice> findFuturePrices(
            @Param("inventoryId") Long inventoryId,
            @Param("date") LocalDate date
    );

    @Query("""
            select p
            from InventoryPrice p
            where p.inventory.id in :inventoryIds
              and p.effectiveFrom > :date
            order by p.inventory.id asc, p.effectiveFrom asc, p.id asc
            """)
    List<InventoryPrice> findFuturePricesForInventoryIds(
            @Param("inventoryIds") Collection<Long> inventoryIds,
            @Param("date") LocalDate date
    );

    List<InventoryPrice> findAllByInventoryIdOrderByEffectiveFromDescIdDesc(Long inventoryId);

    @Query("""
            select p
            from InventoryPrice p
            join fetch p.inventory i
            where i.bookstore.id = :bookstoreId
            order by i.id asc, p.effectiveFrom desc, p.id desc
            """)
    List<InventoryPrice> findAllByBookstoreId(@Param("bookstoreId") Long bookstoreId);

    @Query("""
            select p
            from InventoryPrice p
            join fetch p.inventory i
            where p.effectiveFrom <= :date
              and i.active = true
            order by i.id asc, p.effectiveFrom desc, p.id desc
            """)
    List<InventoryPrice> findAllCurrentCandidates(@Param("date") LocalDate date);
}
