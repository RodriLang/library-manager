package com.rodrilang.librarymanager.purchasing.model;

import com.rodrilang.librarymanager.provider.model.Provider;
import com.rodrilang.librarymanager.purchasing.payment.model.PurchasePayment;
import com.rodrilang.librarymanager.purchasing.payment.model.PurchasePaymentStatus;
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

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL)
    @OrderBy("paidAt ASC, id ASC")
    @Builder.Default
    private List<PurchasePayment> payments = new ArrayList<>();

    public BigDecimal getPaidAmount() {
        return payments.stream()
                .filter(PurchasePayment::isActive)
                .map(PurchasePayment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2);
    }

    public BigDecimal getPendingAmount() {
        return totalAmount.subtract(getPaidAmount()).max(BigDecimal.ZERO).setScale(2);
    }

    public PurchasePaymentStatus getPaymentStatus() {
        BigDecimal paidAmount = getPaidAmount();
        if (paidAmount.signum() == 0 || totalAmount.signum() == 0) {
            return PurchasePaymentStatus.PENDING;
        }
        return paidAmount.compareTo(totalAmount) >= 0
                ? PurchasePaymentStatus.PAID
                : PurchasePaymentStatus.PARTIALLY_PAID;
    }
}

