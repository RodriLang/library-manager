package com.rodrilang.librarymanager.inventory.bulk.model;

import com.rodrilang.librarymanager.auth.models.User;
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

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "inventory_bulk_operations")
public class InventoryBulkOperation extends AuditableEntity {

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
    @Column(nullable = false, length = 50)
    private InventoryBulkAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "selection_type", nullable = false, length = 20)
    private InventoryBulkSelectionType selectionType;

    @Column(name = "selected_count", nullable = false)
    private Integer selectedCount;

    @Column(name = "affected_count", nullable = false)
    private Integer affectedCount;

    @Column(name = "skipped_count", nullable = false)
    private Integer skippedCount;

    @Column(name = "parameter_value", length = 255)
    private String parameterValue;

    @Column(name = "selection_summary", length = 1000)
    private String selectionSummary;
}