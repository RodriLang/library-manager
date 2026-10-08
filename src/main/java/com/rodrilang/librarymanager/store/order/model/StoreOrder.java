package com.rodrilang.librarymanager.store.order.model;

import com.rodrilang.librarymanager.model.AuditableEntity;
import com.rodrilang.librarymanager.model.Bookstore;
import com.rodrilang.librarymanager.store.model.BookstoreStore;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@Entity
@Table(name = "store_orders", uniqueConstraints = {
        @UniqueConstraint(name = "uq_store_order_client_request", columnNames = {"store_id", "client_request_id"})
})
public class StoreOrder extends AuditableEntity {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "public_id", nullable = false, unique = true) private UUID publicId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "store_id", nullable = false) private BookstoreStore store;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "bookstore_id", nullable = false) private Bookstore bookstore;
    @Column(name = "client_request_id", nullable = false) private UUID clientRequestId;
    @Column(name = "order_number", nullable = false, unique = true, length = 40) private String orderNumber;
    @Column(name = "tracking_token", nullable = false, unique = true) private UUID trackingToken;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30) private StoreOrderStatus status;
    @Enumerated(EnumType.STRING) @Column(name = "payment_status", nullable = false, length = 30) private StorePaymentStatus paymentStatus;
    @Enumerated(EnumType.STRING) @Column(name = "fulfillment_status", nullable = false, length = 40) private StoreFulfillmentStatus fulfillmentStatus;
    @Enumerated(EnumType.STRING) @Column(name = "payment_method", nullable = false, length = 40) private StorePaymentMethod paymentMethod;
    @Enumerated(EnumType.STRING) @Column(name = "delivery_method", nullable = false, length = 40) private StoreDeliveryMethod deliveryMethod;
    @Column(name = "customer_name", nullable = false, length = 200) private String customerName;
    @Column(name = "customer_email", nullable = false, length = 320) private String customerEmail;
    @Column(name = "customer_phone", length = 80) private String customerPhone;
    @Column(columnDefinition = "TEXT") private String notes;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal subtotal;
    @Column(name = "discount_amount", nullable = false, precision = 14, scale = 2) private BigDecimal discountAmount;
    @Column(name = "shipping_cost", nullable = false, precision = 14, scale = 2) private BigDecimal shippingCost;
    @Column(nullable = false, precision = 14, scale = 2) private BigDecimal total;
    @Column(name = "reservation_expires_at") private Instant reservationExpiresAt;
    @Column(name = "confirmed_at") private Instant confirmedAt;
    @Column(name = "cancelled_at") private Instant cancelledAt;
    @Column(name = "cancellation_reason", length = 500) private String cancellationReason;
}
