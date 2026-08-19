package com.rodrilang.librarymanager.purchasing.requirement.model;

import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListProvider;
import com.rodrilang.librarymanager.model.AuditableEntity;
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
        name = "purchase_requirement_sources",
        indexes = {
                @Index(
                        name = "idx_purchase_requirement_sources_requirement",
                        columnList = "purchase_requirement_id"
                ),
                @Index(
                        name = "idx_purchase_requirement_sources_reference",
                        columnList = "source_type, reference_id"
                )
        }
)
public class PurchaseRequirementSource extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "purchase_requirement_id",
            nullable = false,
            foreignKey = @ForeignKey(
                    name = "fk_purchase_requirement_sources_requirement"
            )
    )
    private PurchaseRequirement requirement;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 30)
    private PurchaseRequirementSourceType type;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "reference_id", length = 100)
    private String referenceId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "provider_id",
            foreignKey = @ForeignKey(
                    name = "fk_purchase_requirement_sources_provider"
            )
    )
    private PriceListProvider provider;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversed_source_id")
    private PurchaseRequirementSource reversedSource;
}