package com.rodrilang.librarymanager.integrations.tiendanube.entity;

import com.rodrilang.librarymanager.model.Bookstore;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "tiendanube_oauth_states",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_tiendanube_oauth_state_hash",
                        columnNames = "state_hash"
                )
        },
        indexes = {
                @Index(
                        name = "idx_tiendanube_oauth_states_expires_at",
                        columnList = "expires_at"
                ),
                @Index(
                        name = "idx_tiendanube_oauth_states_bookstore_id",
                        columnList = "bookstore_id"
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TiendanubeOAuthState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "state_hash", nullable = false, length = 64)
    private String stateHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bookstore_id", nullable = false)
    private Bookstore bookstore;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}