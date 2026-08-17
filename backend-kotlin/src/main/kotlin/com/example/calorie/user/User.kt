package com.example.calorie.user

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.time.OffsetDateTime

/**
 * アプリケーションのユーザー。
 *
 * メールアドレスは小文字に正規化してから保存する。DB のユニーク制約は素の文字列に
 * 対して張られているため、正規化はアプリケーション側の責務となる。
 * 正規化は [create] を通すことで一箇所に閉じている。
 *
 * **data class にしていない。** JPA エンティティの同一性は ID で判定すべきもので、
 * data class が生成する `equals`/`hashCode` は全プロパティを見るため、
 * 遅延ロードや ID 未確定の状態で破綻する。
 */
@Entity
@Table(name = "users")
class User protected constructor() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(name = "email", nullable = false, length = 255)
    lateinit var email: String
        protected set

    @Column(name = "password_hash", nullable = false, length = 100)
    lateinit var passwordHash: String
        protected set

    @Column(name = "display_name", nullable = false, length = 50)
    lateinit var displayName: String
        protected set

    @Column(name = "created_at", nullable = false)
    lateinit var createdAt: OffsetDateTime
        protected set

    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: OffsetDateTime
        protected set

    @PreUpdate
    protected fun onUpdate() {
        updatedAt = OffsetDateTime.now()
    }

    companion object {
        /**
         * 新規ユーザーを生成する。メールアドレスの正規化はここでのみ行う。
         *
         * @param rawEmail 入力されたメールアドレス（大文字・前後空白を含みうる）
         * @param passwordHash BCrypt でハッシュ化済みのパスワード。生パスワードを渡してはならない。
         */
        fun create(rawEmail: String, passwordHash: String, displayName: String): User =
            User().apply {
                this.email = normalizeEmail(rawEmail)
                this.passwordHash = passwordHash
                this.displayName = displayName
                val now = OffsetDateTime.now()
                this.createdAt = now
                this.updatedAt = now
            }

        /** メールアドレスの正規化ルール。検索時にも同じ変換を通す必要がある。 */
        fun normalizeEmail(rawEmail: String): String = rawEmail.trim().lowercase()
    }
}
