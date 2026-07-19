package com.rodrilang.librarymanager.integrations.tiendanube.entity;

import com.rodrilang.librarymanager.model.Bookstore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "tiendanube_stores")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TiendanubeStore {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "bookstore_id", nullable = false, unique = true)
    private Bookstore bookstore;

    @Column(name = "store_id", nullable = false, unique = true)
    private Long storeId;

    @Column(name = "access_token", nullable = false, length = 1000)
    private String accessToken;

    // TODO: Encriptar mediante AttributeConverter (AES-GCM)
    @Column(name = "token_type", length = 20)
    private String tokenType;

    @Column(name = "scope", length = 500)
    private String scope;

    @Builder.Default
    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    @Column(name = "last_error", length = 1000)
    private String lastError;
}