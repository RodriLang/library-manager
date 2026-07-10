package com.rodrilang.librarymanager.integrations.tiendanube.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

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

    @Column(name = "store_id", nullable = false, unique = true)
    private Long storeId;

    @Column(name = "access_token", nullable = false, length = 500)
    private String accessToken;

    @Column(name = "token_type")
    private String tokenType;

    @Column(name = "scope")
    private String scope;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "connected_at", nullable = false)
    private LocalDateTime connectedAt;
}