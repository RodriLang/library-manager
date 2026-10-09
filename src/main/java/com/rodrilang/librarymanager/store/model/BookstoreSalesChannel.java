package com.rodrilang.librarymanager.store.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.store.channel.SalesChannelType;
import jakarta.persistence.*;
import lombok.*;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "bookstore_sales_channels", uniqueConstraints = @UniqueConstraint(name = "uq_bookstore_sales_channel", columnNames = {"bookstore_id", "channel"}))
public class BookstoreSalesChannel extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "bookstore_id", nullable = false) private Bookstore bookstore;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 40) private SalesChannelType channel;
    @Builder.Default @Column(nullable = false) private Boolean enabled = false;
}
