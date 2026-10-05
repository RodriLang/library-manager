package com.rodrilang.librarymanager.provider.bookstore.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.provider.model.Provider;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name="bookstore_providers", uniqueConstraints=@UniqueConstraint(name="uk_bookstore_providers", columnNames={"bookstore_id","provider_id"}), indexes={
        @Index(name="idx_bookstore_providers_bookstore", columnList="bookstore_id"),
        @Index(name="idx_bookstore_providers_provider", columnList="provider_id")
})
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BookstoreProvider extends AuditableEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="bookstore_id", nullable=false) private Bookstore bookstore;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="provider_id", nullable=false) private Provider provider;
    @Column(nullable=false) @Builder.Default private boolean active=true;
    @Column(name="preferred", nullable=false) @Builder.Default private boolean preferred=false;
    @Column(columnDefinition="TEXT") private String notes;
}
