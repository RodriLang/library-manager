package com.rodrilang.librarymanager.editorialprice.model;

import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceConfirmationSourceType;
import com.rodrilang.librarymanager.editorialprice.enums.ExternalPriceSourceType;
import com.rodrilang.librarymanager.importer.price.configuration.model.PriceListProvider;
import com.rodrilang.librarymanager.model.EditorialPrice;
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
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "editorial_price_confirmations")
public class EditorialPriceConfirmation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "editorial_price_id", nullable = false)
    private EditorialPrice editorialPrice;

    @Column(name = "confirmed_on", nullable = false)
    private LocalDate confirmedOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", nullable = false, length = 20)
    private EditorialPriceConfirmationSourceType sourceType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "provider_id")
    private PriceListProvider provider;

    @Enumerated(EnumType.STRING)
    @Column(name = "external_source_type", length = 30)
    private ExternalPriceSourceType externalSourceType;

    @Column(name = "source_name")
    private String sourceName;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "created_by_username", nullable = false)
    private String createdByUsername;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}