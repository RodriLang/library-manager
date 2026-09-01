package com.rodrilang.librarymanager.editorialprice.model;

import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceAuthority;
import com.rodrilang.librarymanager.editorialprice.enums.EffectiveEditorialPriceDeterminationType;
import com.rodrilang.librarymanager.editorialprice.enums.EffectiveEditorialPriceInvalidationReason;
import com.rodrilang.librarymanager.model.Book;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "effective_editorial_prices")
public class EffectiveEditorialPrice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Builder.Default
    @Column(nullable = false, length = 3)
    private String currency = "ARS";

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Enumerated(EnumType.STRING)
    @Column(name = "determination_type", nullable = false, length = 40)
    private EffectiveEditorialPriceDeterminationType determinationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private EditorialPriceAuthority authority;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_editorial_price_id")
    private EditorialPrice selectedEditorialPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolution_id")
    private EditorialPriceResolution resolution;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "invalidation_reason", length = 40)
    private EffectiveEditorialPriceInvalidationReason invalidationReason;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}