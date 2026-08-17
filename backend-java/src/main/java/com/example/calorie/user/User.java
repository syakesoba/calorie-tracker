package com.example.calorie.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * アプリケーションのユーザー。
 *
 * <p>メールアドレスは小文字に正規化してから保存する。DB のユニーク制約は
 * 素の文字列に対して張られているため、正規化はアプリケーション側の責務となる。
 * 正規化は {@link User#create} を通すことで一箇所に閉じている。
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 50)
    private String displayName;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** JPA が要求する引数なしコンストラクタ。アプリケーションコードからは使わない。 */
    protected User() {
    }

    private User(String email, String passwordHash, String displayName) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.displayName = displayName;
        OffsetDateTime now = OffsetDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * 新規ユーザーを生成する。メールアドレスの正規化はここでのみ行う。
     *
     * @param rawEmail     入力されたメールアドレス（大文字・前後空白を含みうる）
     * @param passwordHash BCrypt でハッシュ化済みのパスワード。生パスワードを渡してはならない。
     * @param displayName  表示名
     */
    public static User create(String rawEmail, String passwordHash, String displayName) {
        return new User(normalizeEmail(rawEmail), passwordHash, displayName);
    }

    /** メールアドレスの正規化ルール。検索時にも同じ変換を通す必要がある。 */
    public static String normalizeEmail(String rawEmail) {
        return rawEmail.trim().toLowerCase();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
