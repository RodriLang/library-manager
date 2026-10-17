package com.rodrilang.librarymanager.store.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "store_sections", uniqueConstraints = @UniqueConstraint(name = "uq_store_section_slug", columnNames = {"store_id", "slug"}))
public class StoreSection extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "store_id", nullable = false) private BookstoreStore store;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private StoreSectionType type;
    @Column(nullable = false, length = 150) private String name;
    @Column(nullable = false, length = 120) private String slug;
    @Builder.Default @Column(nullable = false) private Boolean enabled = true;
    @Builder.Default @Column(name = "display_order", nullable = false) private Integer displayOrder = 0;
    @Column(name = "window_days") private Integer windowDays;
    @Builder.Default @Column(name = "max_items", nullable = false) private Integer maxItems = 8;
}
