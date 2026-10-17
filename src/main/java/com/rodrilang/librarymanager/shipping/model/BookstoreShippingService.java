package com.rodrilang.librarymanager.shipping.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Bookstore;
import jakarta.persistence.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "bookstore_shipping_services", uniqueConstraints = @UniqueConstraint(name = "uq_bookstore_shipping_service", columnNames = {"bookstore_id", "provider", "service_code"}))
public class BookstoreShippingService extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bookstore_id", nullable = false)
    private Bookstore bookstore;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ShippingProviderType provider;

    @Column(name = "service_code", nullable = false, length = 100)
    private String serviceCode;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type", nullable = false, length = 30)
    private ShippingDeliveryType deliveryType;

    @Builder.Default
    @Column(nullable = false)
    private Boolean enabled = true;

    @Builder.Default
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Column(name = "settings_json", columnDefinition = "TEXT")
    private String settingsJson;
}
