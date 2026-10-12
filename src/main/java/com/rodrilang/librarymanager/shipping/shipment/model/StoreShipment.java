package com.rodrilang.librarymanager.shipping.shipment.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import com.rodrilang.librarymanager.store.order.model.StoreOrder;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "store_shipments")
public class StoreShipment extends AuditableEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bookstore_id", nullable = false)
    private Bookstore bookstore;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private StoreOrder order;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ShippingProviderType provider;

    @Column(name = "service_code", length = 100)
    private String serviceCode;

    @Column(name = "service_name", length = 150)
    private String serviceName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ShipmentStatus status;

    @Column(name = "provider_shipment_id", length = 160)
    private String providerShipmentId;

    @Column(name = "tracking_number", length = 160)
    private String trackingNumber;

    @Column(name = "tracking_url", columnDefinition = "TEXT")
    private String trackingUrl;

    @Column(name = "label_url", columnDefinition = "TEXT")
    private String labelUrl;

    @Column(name = "provider_status", length = 120)
    private String providerStatus;

    @Column(name = "provider_status_detail", columnDefinition = "TEXT")
    private String providerStatusDetail;

    @Column(name = "weight_grams")
    private Integer weightGrams;

    @Column(name = "width_cm", precision = 10, scale = 2)
    private BigDecimal widthCm;

    @Column(name = "height_cm", precision = 10, scale = 2)
    private BigDecimal heightCm;

    @Column(name = "depth_cm", precision = 10, scale = 2)
    private BigDecimal depthCm;

    @Column(name = "declared_value", precision = 14, scale = 2)
    private BigDecimal declaredValue;

    @Column(name = "package_sequence", nullable = false)
    private Integer packageSequence;

    @Column(columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at_provider")
    private Instant createdAtProvider;

    @Column(name = "dispatched_at")
    private Instant dispatchedAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;
}
