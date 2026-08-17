package com.example.calorie.auth

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.OffsetDateTime

/**
 * リフレッシュトークン。
 *
 * 生のトークン文字列は保存せず、SHA-256 ハッシュのみを保存する。
 * DB が漏れてもトークンそのものは復元できないようにするため。
 * アクセストークン（JWT）と違い DB に実体があるので、個別に失効させられる。
 */
@Entity
@Table(name = "refresh_tokens")
class RefreshToken protected constructor() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0
        protected set

    @Column(name = "token_hash", nullable = false, length = 64)
    lateinit var tokenHash: String
        protected set

    @Column(name = "expires_at", nullable = false)
    lateinit var expiresAt: OffsetDateTime
        protected set

    @Column(name = "revoked_at")
    var revokedAt: OffsetDateTime? = null
        protected set

    @Column(name = "created_at", nullable = false)
    lateinit var createdAt: OffsetDateTime
        protected set

    /** 失効させる。すでに失効済みの場合は何もしない（冪等）。 */
    fun revoke() {
        if (revokedAt == null) {
            revokedAt = OffsetDateTime.now()
        }
    }

    /** 有効期限内かつ未失効であれば true。 */
    fun isUsable(now: OffsetDateTime): Boolean =
        revokedAt == null && expiresAt.isAfter(now)

    companion object {
        fun issue(userId: Long, tokenHash: String, expiresAt: OffsetDateTime): RefreshToken =
            RefreshToken().apply {
                this.userId = userId
                this.tokenHash = tokenHash
                this.expiresAt = expiresAt
                this.createdAt = OffsetDateTime.now()
            }
    }
}
