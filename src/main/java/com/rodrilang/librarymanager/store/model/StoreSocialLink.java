package com.rodrilang.librarymanager.store.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "store_social_links", uniqueConstraints = @UniqueConstraint(name = "uq_store_social_link_type", columnNames = {"store_id", "type"}))
public class StoreSocialLink extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "store_id", nullable = false) private BookstoreStore store;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private StoreSocialLinkType type;
    @Column(nullable = false, length = 1000) private String url;
    @Builder.Default @Column(nullable = false) private Boolean enabled = true;
    @Builder.Default @Column(name = "display_order", nullable = false) private Integer displayOrder = 0;
}
