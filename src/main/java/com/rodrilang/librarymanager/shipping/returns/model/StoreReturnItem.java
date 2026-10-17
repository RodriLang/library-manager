package com.rodrilang.librarymanager.shipping.returns.model;
import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.store.order.model.StoreOrderItem;
import jakarta.persistence.*;
import lombok.*;
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity @Table(name="store_return_items", uniqueConstraints=@UniqueConstraint(name="uq_store_return_item", columnNames={"return_id","order_item_id"}))
public class StoreReturnItem extends AuditableEntity {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="return_id", nullable=false) private StoreReturn storeReturn;
    @ManyToOne(fetch=FetchType.LAZY, optional=false) @JoinColumn(name="order_item_id", nullable=false) private StoreOrderItem orderItem;
    @Column(nullable=false) private Integer quantity;
    @Builder.Default @Column(name="restocked_quantity", nullable=false) private Integer restockedQuantity = 0;
}
