package com.example.calorie.config

import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

/**
 * JWT の設定値。application.yml の `app.jwt.*` に対応する。
 *
 * @param secret HS256 の署名鍵。32 バイト未満だと jjwt が例外を投げるため、
 *   起動時点で弾けるよう `@Size` で検証する。
 * @param accessTokenMinutes アクセストークンの有効期間（分）
 * @param refreshTokenDays リフレッシュトークンの有効期間（日）
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
data class JwtProperties(
    @field:NotBlank
    @field:Size(min = 32, message = "JWT の署名鍵は 32 バイト以上にしてください")
    val secret: String,

    @field:Min(1)
    val accessTokenMinutes: Int,

    @field:Min(1)
    val refreshTokenDays: Int,
)
