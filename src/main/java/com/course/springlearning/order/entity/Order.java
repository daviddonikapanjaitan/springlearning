package com.course.springlearning.order.entity;

import com.course.springlearning.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

// @Getter/@Setter instead of @Data: a generated toString/equals would load the lazy user relation
@Getter
@Setter
@Entity
@Table(name = "orders")
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "users_id", nullable = false, updatable = false)
    private User user;

    @Column(name = "invoice_number", nullable = false, updatable = false, columnDefinition = "text")
    private String invoiceNumber;

    @Column(name = "item_name", nullable = false, columnDefinition = "text")
    private String itemName;

    @Column(name = "quantity", nullable = false)
    private long quantity;

    @Column(name = "item_price", nullable = false)
    private long itemPrice;

    @Column(name = "total_price", nullable = false)
    private long totalPrice;

    @Column(name = "order_description", columnDefinition = "text")
    private String orderDescription;

    @Column(name = "order_receive", nullable = false)
    private boolean orderReceive = false;

    @Enumerated(EnumType.STRING)
    @Column(name = "order_status", nullable = false, columnDefinition = "text")
    private OrderStatus orderStatus = OrderStatus.PENDING;

    // Instant is always UTC (+0)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "created_by", nullable = false, updatable = false, columnDefinition = "text")
    private String createdBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", nullable = false, columnDefinition = "text")
    private String updatedBy;
}
