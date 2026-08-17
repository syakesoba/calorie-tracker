package com.example.calorie.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * JWT の設定値。application.yml の {@code app.jwt.*} に対応する。
 *
 * @param secret             HS256 の署名鍵。32 バイト未満だと jjwt が例外を投げるため、
 *                           起動時点で弾けるよう {@code @Size} で検証する。
 * @param accessTokenMinutes アクセストークンの有効期間（分）
 * @param refreshTokenDays   リフレッシュトークンの有効期間（日）
 */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank @Size(min = 32, message = "JWT の署名鍵は 32 バイト以上にしてください") String secret,
        @Min(1) int accessTokenMinutes,
        @Min(1) int refreshTokenDays
) {
}
