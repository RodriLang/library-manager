package com.rodrilang.librarymanager.purchasing.model;

import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Bookstore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "purchases", indexes = {
        @Index(name = "idx_purchases_bookstore_date", columnList = "bookstore_id,purchase_date"),
        @Index(name = "idx_purchases_provider", columnList = "provider_id")
})
public class Purchase extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bookstore_id", nullable = false)
    private Bookstore bookstore;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "provider_id", nullable = false)
    private Provider provider;

    @Column(name = "purchase_date", nullable = false)
    private LocalDate purchaseDate;

    @Column(name = "document_number", length = 80)
    private String documentNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private PurchaseStatus status = PurchaseStatus.DRAFT;

    @Column(name = "total_amount", nullable = false, precision = 16, scale = 2)
    @Builder.Default
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<PurchaseItem> items = new ArrayList<>();
}
