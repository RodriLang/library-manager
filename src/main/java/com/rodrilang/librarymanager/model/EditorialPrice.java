package com.rodrilang.librarymanager.model;

import com.rodrilang.librarymanager.importer.price.parser.PriceListSource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "editorial_prices",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_editorial_prices_book_source_valid_from",
                columnNames = {"book_id", "source", "valid_from"}
        )
)
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PriceListSource source;

    private LocalDate validFrom;

    @Builder.Default
    @Column(nullable = false)
    private Boolean active = true;
}