package com.example.calorie.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/**
 * 認証まわりの入出力 DTO をまとめたもの。
 *
 * <p>いずれも {@code api/openapi.yaml} のスキーマと 1 対 1 で対応する。
 * 仕様を変えるときは必ず YAML を先に直すこと。
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /** 新規登録リクエスト。 */
    public record SignUpRequest(
            @NotBlank @Email @Size(max = 255) String email,
            @NotBlank @Size(min = 8, max = 72, message = "パスワードは 8 文字以上 72 文字以下にしてください") String password,
            @NotBlank @Size(max = 50) String displayName
    ) {
    }

    /** ログインリクエスト。 */
    public record LoginRequest(
            @NotBlank @Email String email,
            @NotBlank String password
    ) {
    }

    /** トークン更新・ログアウトで使うリクエスト。 */
    public record RefreshTokenRequest(
            @NotBlank String refreshToken
    ) {
    }

    /**
     * トークン応答。
     *
     * @param expiresIn アクセストークンの残り有効期間（秒）
     */
    public record TokenResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn
    ) {
        public static TokenResponse of(String accessToken, String refreshToken, long expiresIn) {
            return new TokenResponse(accessToken, refreshToken, "Bearer", expiresIn);
        }
    }

    /** ログイン中のユーザー情報。パスワードハッシュは絶対に含めない。 */
    public record UserResponse(
            Long id,
            String email,
            String displayName,
            OffsetDateTime createdAt
    ) {
    }
}
