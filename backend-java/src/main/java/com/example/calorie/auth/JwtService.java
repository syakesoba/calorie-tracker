package com.example.calorie.auth;

import com.example.calorie.config.JwtProperties;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.Optional;

/**
 * トークンの発行と検証。
 *
 * <p>アクセストークンは自己完結型の JWT（HS256）で、DB を引かずに検証できる。
 * リフレッシュトークンは単なるランダム文字列で、実体は DB 側にある。
 * JWT にしないのは、失効させられる必要があるため。
 */
@Service
public class JwtService {

    private final SecretKey signingKey;
    private final JwtProperties properties;
    private final SecureRandom random = new SecureRandom();

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
    }

    /**
     * アクセストークンを発行する。
     * subject にユーザー ID を入れ、email はデバッグ用の補助情報として持たせる。
     */
    public String issueAccessToken(Long userId, String email) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.accessTokenMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    /**
     * アクセストークンを検証し、ユーザー ID を取り出す。
     * 署名不正・期限切れ・形式不正はすべて {@link Optional#empty()} として扱う。
     * 呼び出し側で理由を区別する必要がないため。
     */
    public Optional<Long> extractUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(Long.valueOf(claims.getSubject()));
            // IllegalArgumentException は NumberFormatException（subject が数値でない場合）も含む
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    /** アクセストークンの有効期間（秒）。クライアントへ返す用。 */
    public long accessTokenExpiresInSeconds() {
        return properties.accessTokenMinutes() * 60L;
    }

    /** リフレッシュトークンの生文字列を生成する。クライアントへはこの値だけを返す。 */
    public String generateRefreshTokenValue() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** リフレッシュトークンの保存用ハッシュ。DB にはこちらだけを入れる。 */
    public String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 は JVM 必須アルゴリズムなので、ここには到達しない
            throw new IllegalStateException("SHA-256 が利用できません", e);
        }
    }

    public OffsetDateTime refreshTokenExpiresAt() {
        return OffsetDateTime.now().plusDays(properties.refreshTokenDays());
    }
}
