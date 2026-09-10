package com.rodrilang.librarymanager.integrations.tiendanube.management.entity;

import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeBulkAction;
import com.rodrilang.librarymanager.integrations.tiendanube.management.enums.TiendanubeBulkSelectionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "tiendanube_bulk_operations")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TiendanubeBulkOperation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "bookstore_id", nullable = false)
    private Long bookstoreId;

    @Column(name = "tiendanube_store_id", nullable = false)
    private Long tiendanubeStoreId;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 40)
    private TiendanubeBulkAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "selection_type", nullable = false, length = 20)
    private TiendanubeBulkSelectionType selectionType;

    @Column(name = "selection_description", columnDefinition = "TEXT")
    private String selectionDescription;

    @Column(name = "cancel_requested_at")
    private Instant cancelRequestedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    private void prePersist() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
