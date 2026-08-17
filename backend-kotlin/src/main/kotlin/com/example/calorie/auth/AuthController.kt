package com.example.calorie.auth

import com.example.calorie.auth.dto.LoginRequest
import com.example.calorie.auth.dto.RefreshTokenRequest
import com.example.calorie.auth.dto.SignUpRequest
import com.example.calorie.auth.dto.TokenResponse
import com.example.calorie.auth.dto.UserResponse
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

/**
 * 認証エンドポイント。契約は `api/openapi.yaml` の auth 配下と一致させること。
 *
 * 補足: KDoc 内にスラッシュとアスタリスクの並びを書いてはならない。
 * Kotlin はブロックコメントの入れ子を許すため、そこで新しいコメントが開き、
 * 閉じられないまま構文エラーになる。Java では起きない差異。
 */
@RestController
@RequestMapping("/api/auth")
class AuthController(private val authService: AuthService) {

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    fun signUp(@Valid @RequestBody request: SignUpRequest): TokenResponse =
        authService.signUp(request)

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest): TokenResponse =
        authService.login(request)

    @PostMapping("/refresh")
    fun refresh(@Valid @RequestBody request: RefreshTokenRequest): TokenResponse =
        authService.refresh(request.refreshToken)

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(@Valid @RequestBody request: RefreshTokenRequest) =
        authService.logout(request.refreshToken)

    /**
     * ログイン中のユーザー情報。
     *
     * `@AuthenticationPrincipal` で受け取る値は [JwtAuthenticationFilter] が
     * SecurityContext に載せたユーザー ID。
     */
    @GetMapping("/me")
    fun me(@AuthenticationPrincipal userId: Long): UserResponse =
        authService.getCurrentUser(userId)
}
