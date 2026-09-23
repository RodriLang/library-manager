package com.rodrilang.librarymanager.inventory.count.model;

import com.rodrilang.librarymanager.auth.models.User;
import com.rodrilang.librarymanager.enums.BookCondition;
import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Bookstore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "inventory_count_sessions")
public class InventoryCountSession extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bookstore_id", nullable = false)
    private Bookstore bookstore;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InventoryCountMode mode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InventoryCountPurpose purpose;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private InventoryCountStatus status = InventoryCountStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BookCondition condition = BookCondition.NEW;

    @Column(name = "baseline_at", nullable = false)
    private Instant baselineAt;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "reverted_at")
    private Instant revertedAt;

    @Builder.Default
    @Column(name = "default_editorial_price_sync_enabled", nullable = false)
    private Boolean defaultEditorialPriceSyncEnabled = false;

    @Builder.Default
    @Column(name = "default_publish_on_tiendanube", nullable = false)
    private Boolean defaultPublishOnTiendanube = false;

    @Builder.Default
    @Column(name = "default_tiendanube_price_sync_enabled", nullable = false)
    private Boolean defaultTiendanubePriceSyncEnabled = false;

    @Builder.Default
    @Column(name = "default_minimum_stock", nullable = false)
    private Integer defaultMinimumStock = 0;

    @Column(length = 500)
    private String notes;
}
