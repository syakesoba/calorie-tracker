package com.example.calorie.auth

import com.example.calorie.auth.dto.LoginRequest
import com.example.calorie.auth.dto.SignUpRequest
import com.example.calorie.auth.dto.TokenResponse
import com.example.calorie.auth.dto.UserResponse
import com.example.calorie.common.ApiException
import com.example.calorie.user.User
import com.example.calorie.user.UserRepository
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.nio.charset.StandardCharsets
import java.time.OffsetDateTime

/**
 * 登録・ログイン・トークン更新・ログアウト。
 */
@Service
class AuthService(
    private val userRepository: UserRepository,
    private val refreshTokenRepository: RefreshTokenRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
) {

    @Transactional
    fun signUp(request: SignUpRequest): TokenResponse {
        validatePasswordLength(request.password)

        val email = User.normalizeEmail(request.email)
        if (userRepository.existsByEmail(email)) {
            throw ApiException.conflict("EMAIL_ALREADY_USED", "このメールアドレスは既に登録されています。")
        }

        val user = userRepository.save(
            User.create(email, passwordEncoder.encode(request.password), request.displayName)
        )
        return issueTokens(user)
    }

    @Transactional
    fun login(request: LoginRequest): TokenResponse {
        val email = User.normalizeEmail(request.email)
        val user = userRepository.findByEmail(email)

        // ユーザーが見つからない場合もダミーハッシュと照合し、処理時間を揃える
        val storedHash = user?.passwordHash ?: DUMMY_HASH
        val matches = passwordEncoder.matches(request.password, storedHash)

        if (user == null || !matches) {
            // どちらが原因かをクライアントへ伝えない（メールアドレスの存在推測を防ぐ）
            throw ApiException.unauthorized(
                "INVALID_CREDENTIALS", "メールアドレスまたはパスワードが正しくありません。"
            )
        }

        return issueTokens(user)
    }

    /**
     * リフレッシュトークンを使って新しいトークン一式を発行する。
     *
     * 使用済みのトークンはその場で失効させ、新しいものを発行する（ローテーション）。
     * 盗まれたトークンが再利用されたときに、正規の利用者が先に使っていれば弾ける。
     */
    @Transactional
    fun refresh(rawRefreshToken: String): TokenResponse {
        val hash = jwtService.hashRefreshToken(rawRefreshToken)
        val token = refreshTokenRepository.findByTokenHash(hash)
            ?: throw invalidRefreshToken()

        if (!token.isUsable(OffsetDateTime.now())) {
            throw ApiException.unauthorized(
                "INVALID_REFRESH_TOKEN",
                "リフレッシュトークンの有効期限が切れています。再度ログインしてください。",
            )
        }

        token.revoke()

        val user = userRepository.findById(token.userId).orElseThrow { invalidRefreshToken() }
        return issueTokens(user)
    }

    /**
     * ログアウト。渡されたリフレッシュトークンを失効させる。
     *
     * 存在しないトークンを渡されてもエラーにしない。ログアウトは冪等であるべきで、
     * かつ「そのトークンが存在するか」を攻撃者に教えないため。
     */
    @Transactional
    fun logout(rawRefreshToken: String) {
        val hash = jwtService.hashRefreshToken(rawRefreshToken)
        refreshTokenRepository.findByTokenHash(hash)?.revoke()
    }

    @Transactional(readOnly = true)
    fun getCurrentUser(userId: Long): UserResponse {
        val user = userRepository.findById(userId)
            .orElseThrow { ApiException.unauthorized("USER_NOT_FOUND", "ユーザーが見つかりません。") }
        return UserResponse(user.id!!, user.email, user.displayName, user.createdAt)
    }

    private fun issueTokens(user: User): TokenResponse {
        val userId = requireNotNull(user.id) { "保存済みのユーザーには ID が付いているはず" }
        val accessToken = jwtService.issueAccessToken(userId, user.email)
        val rawRefreshToken = jwtService.generateRefreshTokenValue()

        refreshTokenRepository.save(
            RefreshToken.issue(
                userId,
                jwtService.hashRefreshToken(rawRefreshToken),
                jwtService.refreshTokenExpiresAt(),
            )
        )

        return TokenResponse.of(accessToken, rawRefreshToken, jwtService.accessTokenExpiresInSeconds())
    }

    private fun invalidRefreshToken() = ApiException.unauthorized(
        "INVALID_REFRESH_TOKEN", "リフレッシュトークンが無効です。再度ログインしてください。"
    )

    private fun validatePasswordLength(password: String) {
        val bytes = password.toByteArray(StandardCharsets.UTF_8).size
        if (bytes > BCRYPT_MAX_PASSWORD_BYTES) {
            throw ApiException.badRequest(
                "PASSWORD_TOO_LONG",
                "パスワードが長すぎます。UTF-8 で $BCRYPT_MAX_PASSWORD_BYTES バイト以内にしてください。",
            )
        }
    }

    private companion object {
        /** BCrypt はパスワードを 72 バイトまでしか扱えない。日本語は 1 文字 3 バイトになりうる。 */
        const val BCRYPT_MAX_PASSWORD_BYTES = 72

        /**
         * ユーザーが存在しない場合でもパスワード照合と同等の時間をかけるためのダミーハッシュ。
         * 応答時間の差からメールアドレスの存在有無を推測されるのを防ぐ。
         */
        const val DUMMY_HASH = "\$2a\$10\$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy"
    }
}
