package com.rodrilang.librarymanager.fiscal.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Bookstore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "bookstore_fiscal_settings",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_bookstore_fiscal_settings_cuit_pos",
                        columnNames = {"cuit", "point_of_sale"}
                )
        }
)
public class BookstoreFiscalSettings extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bookstore_id", nullable = false, unique = true)
    private Bookstore bookstore;

    @Column(nullable = false, length = 11)
    private String cuit;

    @Column(name = "legal_name", nullable = false, length = 200)
    private String legalName;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_condition", nullable = false, length = 40)
    private IssuerTaxCondition taxCondition;

    @Enumerated(EnumType.STRING)
    @Column(name = "gross_income_regime", length = 40)
    private GrossIncomeRegime grossIncomeRegime;

    @Column(name = "gross_income_number", length = 50)
    private String grossIncomeNumber;

    @Column(name = "activity_start_date", nullable = false)
    private LocalDate activityStartDate;

    @Column(name = "fiscal_address", nullable = false, length = 250)
    private String fiscalAddress;

    @Column(nullable = false, length = 120)
    private String city;

    @Column(nullable = false, length = 120)
    private String province;

    @Column(name = "postal_code", length = 20)
    private String postalCode;

    @Column(name = "point_of_sale", nullable = false)
    private Integer pointOfSale;

    @Enumerated(EnumType.STRING)
    @Column(name = "arca_status", nullable = false, length = 40)
    @Builder.Default
    private ArcaAuthorizationStatus arcaStatus = ArcaAuthorizationStatus.PENDING_AUTHORIZATION;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "last_verification_error", length = 1000)
    private String lastVerificationError;
}
