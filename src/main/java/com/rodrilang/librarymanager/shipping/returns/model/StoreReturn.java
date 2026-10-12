package com.rodrilang.librarymanager.shipping.returns.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.shipping.model.ShippingProviderType;
import com.rodrilang.librarymanager.shipping.shipment.model.StoreShipment;
import com.rodrilang.librarymanager.store.order.model.StoreOrder;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="store_returns")
public class StoreReturn extends AuditableEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="bookstore_id", nullable=false) private Bookstore bookstore;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="order_id", nullable=false) private StoreOrder order;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="shipment_id") private StoreShipment shipment;
    @Enumerated(EnumType.STRING) @Column(nullable=false, length=40) private StoreReturnStatus status;
    @Column(length=120) private String reason;
    @Column(columnDefinition="TEXT") private String notes;
    @Enumerated(EnumType.STRING) @Column(length=40) private ShippingProviderType provider;
    @Column(name="provider_return_id", length=160) private String providerReturnId;
    @Column(name="tracking_number", length=160) private String trackingNumber;
    @Column(name="tracking_url", columnDefinition="TEXT") private String trackingUrl;
    @Column(name="requested_at", nullable=false) private Instant requestedAt;
    @Column(name="approved_at") private Instant approvedAt;
    @Column(name="received_at") private Instant receivedAt;
    @Column(name="completed_at") private Instant completedAt;
    @Column(name="cancelled_at") private Instant cancelledAt;
}
