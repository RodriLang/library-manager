package com.rodrilang.librarymanager.integrations.tiendanube.entity;

import com.rodrilang.librarymanager.model.Book;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(
        name = "tiendanube_product_links",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_tiendanube_store_variant",
                        columnNames = {"tiendanube_store_id", "tiendanube_variant_id"}
                )
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TiendanubeProductLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id", nullable = false)
    private Book book;

    @Column(name = "tiendanube_store_id", nullable = false)
    private Long tiendanubeStoreId;

    @Column(name = "tiendanube_product_id", nullable = false)
    private Long tiendanubeProductId;

    @Column(name = "tiendanube_variant_id", nullable = false)
    private Long tiendanubeVariantId;

    @Column(name = "sku")
    private String sku;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}