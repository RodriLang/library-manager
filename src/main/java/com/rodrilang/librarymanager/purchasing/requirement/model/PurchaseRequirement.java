package com.rodrilang.librarymanager.purchasing.requirement.model;

import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListProvider;
import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Book;
import com.rodrilang.librarymanager.model.Bookstore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "purchase_requirements",
        indexes = {
                @Index(
                        name = "idx_purchase_requirements_bookstore_status",
                        columnList = "bookstore_id, status"
                ),
                @Index(
                        name = "idx_purchase_requirements_provider",
                        columnList = "preferred_provider_id"
                )
        }
)
public class PurchaseRequirement extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bookstore_id", nullable = false)
    private Bookstore bookstore;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(nullable = false)
    private Integer quantity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "preferred_provider_id",
            foreignKey = @ForeignKey(
                    name = "fk_purchase_requirements_provider"
            )
    )
    private PriceListProvider preferredProvider;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private PurchaseRequirementStatus status =
            PurchaseRequirementStatus.PENDING;
}