package com.rodrilang.librarymanager.inventory.pricing.job.repository;

import com.rodrilang.librarymanager.inventory.pricing.job.model.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface InventoryPriceImportJobRepository extends JpaRepository<InventoryPriceImportJob, Long> {
    Optional<InventoryPriceImportJob> findByPriceImportIdAndType(Long importId, InventoryPriceImportJobType type);
    List<InventoryPriceImportJob> findAllByPriceImportIdOrderByIdAsc(Long importId);

    @Modifying
    @Query(value = """
            UPDATE inventory_price_import_jobs
               SET status = 'PENDING',
                   started_at = NULL,
                   heartbeat_at = NULL,
                   error_message = NULL,
                   updated_at = NOW()
             WHERE status = 'PROCESSING'
               AND (heartbeat_at IS NULL OR heartbeat_at < :threshold)
            """, nativeQuery = true)
    int requeueStale(@Param("threshold") Instant threshold);
}
