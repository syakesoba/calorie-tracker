package com.example.calorie.config

import com.example.calorie.auth.JwtAuthenticationFilter
import com.example.calorie.common.ApiErrorResponse
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

/**
 * Spring Security の設定。
 *
 * JWT によるステートレス認証のため、セッションと CSRF トークンは使わない。
 * CSRF を無効化してよいのは、Cookie ではなく Authorization ヘッダで認証しており、
 * ブラウザが自動で資格情報を送らないため。
 */
@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthenticationFilter: JwtAuthenticationFilter,
    private val objectMapper: ObjectMapper,
    @Value("\${app.cors.allowed-origins}") private val allowedOrigins: List<String>,
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/api/auth/signup",
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/api/auth/logout",
                    ).permitAll()
                    .requestMatchers("/actuator/health").permitAll()
                    .anyRequest().authenticated()
            }
            // 未認証・権限不足のときも、他のエラーと同じ JSON 形式で返す
            .exceptionHandling { eh ->
                eh
                    .authenticationEntryPoint { _, response, _ ->
                        writeError(
                            response, HttpServletResponse.SC_UNAUTHORIZED,
                            "UNAUTHORIZED", "認証が必要です。",
                        )
                    }
                    .accessDeniedHandler { _, response, _ ->
                        writeError(
                            response, HttpServletResponse.SC_FORBIDDEN,
                            "FORBIDDEN", "この操作を行う権限がありません。",
                        )
                    }
            }
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter::class.java)

        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder =
        // strength 10 は Spring Security の既定値。**Java 側と同じ値でなければならない。**
        // 値が違うと、片方で登録したユーザーのパスワードを他方で照合できない。
        BCryptPasswordEncoder(10)

    /**
     * CORS 設定。
     *
     * Expo の実機は PC の LAN IP へアクセスするため、許可オリジンは
     * 環境ごとに application.yml で変えられるようにしてある。
     */
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration().apply {
            allowedOriginPatterns = allowedOrigins
            allowedMethods = listOf("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
            allowedHeaders = listOf(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE)
            maxAge = 3600L
        }
        return UrlBasedCorsConfigurationSource().apply {
            registerCorsConfiguration("/api/**", config)
        }
    }

    private fun writeError(response: HttpServletResponse, status: Int, code: String, message: String) {
        response.status = status
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.characterEncoding = "UTF-8"
        objectMapper.writeValue(response.writer, ApiErrorResponse.of(code, message))
    }
}
