package com.rodrilang.librarymanager.inventory.pricing.job.model;

import com.rodrilang.librarymanager.inventory.pricing.model.InventoryPriceImport;
import com.rodrilang.librarymanager.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "inventory_price_import_jobs",
        uniqueConstraints = @UniqueConstraint(name = "uk_inventory_price_import_jobs", columnNames = {"import_id", "job_type"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InventoryPriceImportJob extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "import_id", nullable = false)
    private InventoryPriceImport priceImport;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_type", nullable = false, length = 40)
    private InventoryPriceImportJobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InventoryPriceImportJobStatus status;

    @Builder.Default @Column(name = "total_rows", nullable = false) private Integer totalRows = 0;
    @Builder.Default @Column(name = "processed_rows", nullable = false) private Integer processedRows = 0;
    @Builder.Default @Column(name = "success_rows", nullable = false) private Integer successRows = 0;
    @Builder.Default @Column(name = "skipped_rows", nullable = false) private Integer skippedRows = 0;
    @Builder.Default @Column(name = "error_rows", nullable = false) private Integer errorRows = 0;
    @Builder.Default @Column(name = "last_staging_id", nullable = false) private Long lastStagingId = 0L;

    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(name = "heartbeat_at") private Instant heartbeatAt;
    @Column(name = "error_message", columnDefinition = "TEXT") private String errorMessage;
    @Column(name = "requested_by_user_id") private Long requestedByUserId;
}
