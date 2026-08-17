package com.example.calorie.auth

import com.example.calorie.config.JwtProperties
import io.jsonwebtoken.JwtException
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Date
import java.util.HexFormat

/**
 * トークンの発行と検証。
 *
 * アクセストークンは自己完結型の JWT（HS256）で、DB を引かずに検証できる。
 * リフレッシュトークンは単なるランダム文字列で、実体は DB 側にある。
 * JWT にしないのは、失効させられる必要があるため。
 */
@Service
class JwtService(private val properties: JwtProperties) {

    private val signingKey = Keys.hmacShaKeyFor(properties.secret.toByteArray(StandardCharsets.UTF_8))
    private val random = SecureRandom()

    /**
     * アクセストークンを発行する。
     * subject にユーザー ID を入れ、email はデバッグ用の補助情報として持たせる。
     */
    fun issueAccessToken(userId: Long, email: String): String {
        val now = Instant.now()
        val expiresAt = now.plus(properties.accessTokenMinutes.toLong(), ChronoUnit.MINUTES)
        return Jwts.builder()
            .subject(userId.toString())
            .claim("email", email)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiresAt))
            .signWith(signingKey)
            .compact()
    }

    /**
     * アクセストークンを検証し、ユーザー ID を取り出す。
     * 署名不正・期限切れ・形式不正はすべて null として扱う。
     * 呼び出し側で理由を区別する必要がないため。
     *
     * Java 側は `Optional<Long>` を返しているが、Kotlin では null 許容型で表す。
     */
    fun extractUserId(token: String): Long? =
        try {
            Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .payload
                .subject
                .toLong()
        } catch (e: JwtException) {
            null
        } catch (e: IllegalArgumentException) {
            // subject が数値でない場合の NumberFormatException もここに含まれる
            null
        }

    /** アクセストークンの有効期間（秒）。クライアントへ返す用。 */
    fun accessTokenExpiresInSeconds(): Long = properties.accessTokenMinutes * 60L

    /** リフレッシュトークンの生文字列を生成する。クライアントへはこの値だけを返す。 */
    fun generateRefreshTokenValue(): String {
        val bytes = ByteArray(32)
        random.nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    /** リフレッシュトークンの保存用ハッシュ。DB にはこちらだけを入れる。 */
    fun hashRefreshToken(rawToken: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return HexFormat.of().formatHex(digest.digest(rawToken.toByteArray(StandardCharsets.UTF_8)))
    }

    fun refreshTokenExpiresAt(): OffsetDateTime =
        OffsetDateTime.now().plusDays(properties.refreshTokenDays.toLong())
}
