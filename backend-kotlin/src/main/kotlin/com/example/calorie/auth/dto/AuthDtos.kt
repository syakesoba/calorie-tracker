package com.example.calorie.auth.dto

import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.time.OffsetDateTime

/**
 * 認証まわりの入出力 DTO。
 *
 * いずれも `api/openapi.yaml` のスキーマと 1 対 1 で対応する。
 * 仕様を変えるときは必ず YAML を先に直すこと。
 *
 * Java 側は入れ子クラスを 1 ファイルにまとめているが、Kotlin ではトップレベルに
 * 複数の宣言を置けるため、外側のラッパークラスが要らない。
 */

/** 新規登録リクエスト。 */
data class SignUpRequest(
    @field:NotBlank @field:Email @field:Size(max = 255) val email: String,
    @field:NotBlank
    @field:Size(min = 8, max = 72, message = "パスワードは 8 文字以上 72 文字以下にしてください")
    val password: String,
    @field:NotBlank @field:Size(max = 50) val displayName: String,
)

/** ログインリクエスト。 */
data class LoginRequest(
    @field:NotBlank @field:Email val email: String,
    @field:NotBlank val password: String,
)

/** トークン更新・ログアウトで使うリクエスト。 */
data class RefreshTokenRequest(
    @field:NotBlank val refreshToken: String,
)

/**
 * トークン応答。
 *
 * @param expiresIn アクセストークンの残り有効期間（秒）
 */
data class TokenResponse(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String,
    val expiresIn: Long,
) {
    companion object {
        fun of(accessToken: String, refreshToken: String, expiresIn: Long) =
            TokenResponse(accessToken, refreshToken, "Bearer", expiresIn)
    }
}

/** ログイン中のユーザー情報。パスワードハッシュは絶対に含めない。 */
data class UserResponse(
    val id: Long,
    val email: String,
    val displayName: String,
    val createdAt: OffsetDateTime,
)
