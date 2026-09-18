package com.rodrilang.librarymanager.fiscal.model;

import com.rodrilang.librarymanager.auth.models.User;
import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.sales.model.Sale;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
@Table(
        name = "fiscal_documents",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_fiscal_documents_sale_type",
                        columnNames = {"sale_id", "document_type"}
                )
        },
        indexes = {
                @Index(name = "idx_fiscal_documents_sale", columnList = "sale_id"),
                @Index(name = "idx_fiscal_documents_status", columnList = "bookstore_id,status")
        }
)
public class FiscalDocument extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bookstore_id", nullable = false)
    private Bookstore bookstore;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sale_id", nullable = false)
    private Sale sale;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    @Enumerated(EnumType.STRING)
    @Column(name = "document_type", nullable = false, length = 30)
    private FiscalDocumentType documentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private FiscalDocumentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "voucher_class", nullable = false, length = 5)
    private FiscalVoucherClass voucherClass;

    @Column(name = "voucher_type_code", nullable = false)
    private Integer voucherTypeCode;

    @Column(name = "point_of_sale", nullable = false)
    private Integer pointOfSale;

    @Column(name = "voucher_number")
    private Long voucherNumber;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @Builder.Default
    @Column(nullable = false, length = 3)
    private String currency = "PES";

    @Builder.Default
    @Column(name = "exchange_rate", nullable = false, precision = 18, scale = 6)
    private BigDecimal exchangeRate = BigDecimal.ONE;

    @Builder.Default
    @Column(name = "net_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal netAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "exempt_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal exemptAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "vat_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal vatAmount = BigDecimal.ZERO;

    @Builder.Default
    @Column(name = "tax_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(name = "total_amount", nullable = false, precision = 14, scale = 2)
    private BigDecimal totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_vat_condition", nullable = false, length = 40)
    private RecipientVatCondition recipientVatCondition;

    @Column(name = "recipient_vat_condition_id", nullable = false)
    private Integer recipientVatConditionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "recipient_document_type", nullable = false, length = 30)
    private RecipientDocumentType recipientDocumentType;

    @Column(name = "recipient_document_type_code", nullable = false)
    private Integer recipientDocumentTypeCode;

    @Column(name = "recipient_document_number", length = 20)
    private String recipientDocumentNumber;

    @Column(name = "recipient_name", length = 200)
    private String recipientName;

    @Column(name = "recipient_address", length = 250)
    private String recipientAddress;

    @Column(length = 20)
    private String cae;

    @Column(name = "cae_expiration_date")
    private LocalDate caeExpirationDate;

    @Column(name = "authorized_at")
    private Instant authorizedAt;

    @Column(name = "arca_result", length = 5)
    private String arcaResult;

    @Column(name = "arca_observations", length = 2000)
    private String arcaObservations;

    @Column(name = "arca_errors", length = 2000)
    private String arcaErrors;

    @Column(name = "qr_url", columnDefinition = "TEXT")
    private String qrUrl;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "associated_document_id")
    private FiscalDocument associatedDocument;

    @OneToOne(mappedBy = "associatedDocument", fetch = FetchType.LAZY)
    private FiscalDocument reversingDocument;

    @Column(length = 500)
    private String reason;
}
