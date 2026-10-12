package com.rodrilang.librarymanager.shipping.shipment.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "store_shipment_tracking_events")
public class StoreShipmentTrackingEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "shipment_id", nullable = false)
    private StoreShipment shipment;

    @Column(name = "provider_event_id", length = 180)
    private String providerEventId;

    @Column(name = "provider_status", length = 120)
    private String providerStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "normalized_status", nullable = false, length = 40)
    private ShipmentStatus normalizedStatus;

    @Column(length = 500)
    private String description;

    @Column(length = 250)
    private String location;

    @Column(name = "event_at", nullable = false)
    private Instant eventAt;

    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}
