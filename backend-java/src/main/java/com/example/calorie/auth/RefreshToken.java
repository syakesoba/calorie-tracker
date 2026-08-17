package com.example.calorie.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * リフレッシュトークン。
 *
 * <p>生のトークン文字列は保存せず、SHA-256 ハッシュのみを保存する。
 * DB が漏れてもトークンそのものは復元できないようにするため。
 * アクセストークン（JWT）と違い DB に実体があるので、個別に失効させられる。
 */
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private OffsetDateTime expiresAt;

    @Column(name = "revoked_at")
    private OffsetDateTime revokedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected RefreshToken() {
    }

    private RefreshToken(Long userId, String tokenHash, OffsetDateTime expiresAt) {
        this.userId = userId;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
        this.createdAt = OffsetDateTime.now();
    }

    public static RefreshToken issue(Long userId, String tokenHash, OffsetDateTime expiresAt) {
        return new RefreshToken(userId, tokenHash, expiresAt);
    }

    /** 失効させる。すでに失効済みの場合は何もしない（冪等）。 */
    public void revoke() {
        if (this.revokedAt == null) {
            this.revokedAt = OffsetDateTime.now();
        }
    }

    /** 有効期限内かつ未失効であれば true。 */
    public boolean isUsable(OffsetDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public OffsetDateTime getExpiresAt() {
        return expiresAt;
    }

    public OffsetDateTime getRevokedAt() {
        return revokedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
