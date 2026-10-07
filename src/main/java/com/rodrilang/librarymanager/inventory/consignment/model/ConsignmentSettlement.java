package com.rodrilang.librarymanager.inventory.consignment.model;

import com.rodrilang.librarymanager.auth.models.User;
import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.provider.model.Provider;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "consignment_settlements", uniqueConstraints = @UniqueConstraint(
        name = "uk_consignment_settlement_bookstore_number",
        columnNames = {"bookstore_id", "settlement_number"}
))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ConsignmentSettlement extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bookstore_id", nullable = false)
    private Bookstore bookstore;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Column(name = "settlement_number", nullable = false, length = 50)
    private String settlementNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ConsignmentSettlementStatus status = ConsignmentSettlementStatus.SETTLED;

    @Column(name = "settled_at", nullable = false)
    private Instant settledAt;

    @Column(length = 1000)
    private String notes;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private User createdByUser;
}
