package com.course.springlearning.auth.entity;

import com.course.springlearning.user.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "tokens")
public class Token {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, updatable = false)
    private User user;

    // The signed JWT
    @Column(name = "token", nullable = false, updatable = false, columnDefinition = "text")
    private String token;

    // Same instant as the JWT "exp" claim, always UTC (+0)
    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "expired", nullable = false)
    private boolean expired = false;

    @Column(name = "revoked", nullable = false)
    private boolean revoked = false;

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
