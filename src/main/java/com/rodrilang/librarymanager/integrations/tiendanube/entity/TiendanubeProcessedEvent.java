package com.rodrilang.librarymanager.integrations.tiendanube.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "tiendanube_processed_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_tiendanube_processed_event",
                        columnNames = {"store_id", "resource_id", "event"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TiendanubeProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "store_id", nullable = false)
    private Long storeId;

    @Column(name = "resource_id", nullable = false)
    private Long resourceId;

    @Column(nullable = false, length = 100)
    private String event;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;
}