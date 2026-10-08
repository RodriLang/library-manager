package com.rodrilang.librarymanager.store.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Inventory;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "store_publications", uniqueConstraints = @UniqueConstraint(name = "uq_store_publication_inventory", columnNames = {"store_id", "inventory_id"}))
public class StorePublication extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "store_id", nullable = false) private BookstoreStore store;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "inventory_id", nullable = false) private Inventory inventory;
    @Builder.Default @Column(nullable = false) private Boolean published = false;
    @Column(name = "published_at") private Instant publishedAt;
    @Builder.Default @Column(nullable = false) private Boolean featured = false;
    @Column(name = "featured_order") private Integer featuredOrder;
    @Column(name = "custom_title", length = 500) private String customTitle;
    @Column(name = "custom_description", columnDefinition = "TEXT") private String customDescription;
    @Column(name = "seo_title", length = 255) private String seoTitle;
    @Column(name = "seo_description", length = 500) private String seoDescription;
}
