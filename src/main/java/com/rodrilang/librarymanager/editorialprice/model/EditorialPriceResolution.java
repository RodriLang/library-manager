package com.rodrilang.librarymanager.editorialprice.model;

import com.rodrilang.librarymanager.editorialprice.enums.EditorialPriceResolutionType;
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
@Table(name = "editorial_price_resolutions")
public class EditorialPriceResolution {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "selected_editorial_price_id", nullable = false)
    private EditorialPrice selectedEditorialPrice;

    @Column(name = "resolved_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal resolvedPrice;

    @Column(name = "resolved_currency", nullable = false, length = 3)
    private String resolvedCurrency;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_type", nullable = false, length = 30)
    private EditorialPriceResolutionType resolutionType;

    @Column(columnDefinition = "text")
    private String note;

    @Column(name = "resolved_by_username", nullable = false)
    private String resolvedByUsername;

    @Builder.Default
    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supersedes_resolution_id")
    private EditorialPriceResolution supersedesResolution;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    @Column(name = "deactivated_by_username")
    private String deactivatedByUsername;

    @Column(name = "deactivation_note", columnDefinition = "text")
    private String deactivationNote;
}