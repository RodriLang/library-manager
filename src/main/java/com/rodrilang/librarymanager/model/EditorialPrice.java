package com.rodrilang.librarymanager.model;

import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceOrigin;
import com.rodrilang.librarymanager.editorialprice.enums.ExternalPriceSourceType;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "editorial_prices")
public class EditorialPrice extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    private Book book;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Builder.Default
    @Column(nullable = false, length = 3)
    private String currency = "ARS";

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "provider_id",
            foreignKey = @ForeignKey(name = "fk_editorial_prices_provider")
    )
    private PriceListProvider provider;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false, length = 30)
    private EditorialPriceOrigin origin = EditorialPriceOrigin.PRICE_LIST;

    @Enumerated(EnumType.STRING)
    @Column(name = "external_source_type", length = 30)
    private ExternalPriceSourceType externalSourceType;

    @Column(name = "source_name")
    private String sourceName;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(name = "source_note", columnDefinition = "text")
    private String sourceNote;

    @Column(name = "created_by_username")
    private String createdByUsername;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    @Column(name = "deactivated_by_username")
    private String deactivatedByUsername;

    @Column(name = "deactivation_note", columnDefinition = "text")
    private String deactivationNote;
}