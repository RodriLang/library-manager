package com.rodrilang.librarymanager.inventory.count.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Inventory;
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
@Table(name = "inventory_count_results")
public class InventoryCountResult extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private InventoryCountSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "inventory_id")
    private Inventory inventory;

    @Column(name = "baseline", nullable = false)
    @Builder.Default
    private boolean baseline = false;

    @Column(name = "inventory_existed_before", nullable = false)
    @Builder.Default
    private boolean inventoryExistedBefore = false;

    @Column(name = "previous_active", nullable = false)
    @Builder.Default
    private boolean previousActive = false;

    @Column(name = "previous_quantity", nullable = false)
    @Builder.Default
    private Integer previousQuantity = 0;

    @Column(name = "counted_quantity")
    private Integer countedQuantity;

    @Column(name = "applied_delta", nullable = false)
    @Builder.Default
    private Integer appliedDelta = 0;

    @Column(name = "resulting_quantity")
    private Integer resultingQuantity;

    @Column(name = "resulting_active")
    private Boolean resultingActive;

    @Enumerated(EnumType.STRING)
    @Column(name = "difference_type", length = 20)
    private InventoryCountDifferenceType differenceType;

    @Column(name = "applied_at")
    private Instant appliedAt;

    @Column(name = "reverted_at")
    private Instant revertedAt;
}
