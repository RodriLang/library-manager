package com.rodrilang.librarymanager.shipping.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Bookstore;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bookstore_shipping_settings", uniqueConstraints = @UniqueConstraint(name = "uq_bookstore_shipping_settings_bookstore", columnNames = "bookstore_id"))
public class BookstoreShippingSettings extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bookstore_id", nullable = false)
    private Bookstore bookstore;

    @Column(name = "origin_name", length = 150)
    private String originName;
    @Column(name = "origin_contact_name", length = 150)
    private String originContactName;
    @Column(name = "origin_email", length = 150)
    private String originEmail;
    @Column(name = "origin_phone", length = 50)
    private String originPhone;
    @Column(name = "origin_street", length = 180)
    private String originStreet;
    @Column(name = "origin_street_number", length = 40)
    private String originStreetNumber;
    @Column(name = "origin_floor_apartment", length = 80)
    private String originFloorApartment;
    @Column(name = "origin_postal_code", length = 20)
    private String originPostalCode;
    @Column(name = "origin_city", length = 120)
    private String originCity;
    @Column(name = "origin_state", length = 120)
    private String originState;
    @Builder.Default
    @Column(name = "origin_country_code", nullable = false, length = 2)
    private String originCountryCode = "AR";

    @Builder.Default
    @Column(name = "preparation_days", nullable = false)
    private Integer preparationDays = 1;

    @Column(name = "free_shipping_threshold", precision = 14, scale = 2)
    private BigDecimal freeShippingThreshold;

    @Builder.Default
    @Column(name = "packaging_surcharge", nullable = false, precision = 14, scale = 2)
    private BigDecimal packagingSurcharge = BigDecimal.ZERO;
}
