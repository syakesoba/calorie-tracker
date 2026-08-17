package com.example.calorie.auth

import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 認証フローの結合テスト。
 *
 * **Java 実装の AuthFlowIntegrationTest と同じケースを意図的に並べている。**
 * 対称性を保つことで、テストコードの書き味も比較の材料になる。
 *
 * H2 ではなく実際の PostgreSQL（calorie_test）に対して実行する。
 * **実行前に `docker compose up -d db` で DB を起動しておくこと。**
 * スキーマは Java 側の Flyway が作るため、`backend-java` のテストを
 * 一度も実行していない場合は先にそちらを走らせる。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthFlowIntegrationTest {

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private fun uniqueEmail() = "user-${UUID.randomUUID()}@example.com"

    private fun signUpBody(email: String, password: String) = """
        {"email":"$email","password":"$password","displayName":"テスト太郎"}
    """.trimIndent()

    private fun readField(result: MvcResult, field: String): String =
        objectMapper.readTree(result.response.contentAsString).get(field).asText()

    @Test
    @DisplayName("登録するとトークンが返り、そのトークンで自分の情報を取得できる")
    fun signUpThenFetchMe() {
        val email = uniqueEmail()

        val signUp = mockMvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signUpBody(email, "password1234"))
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.accessToken").isNotEmpty)
            .andExpect(jsonPath("$.refreshToken").isNotEmpty)
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andReturn()

        val accessToken = readField(signUp, "accessToken")

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer $accessToken"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.email").value(email))
            .andExpect(jsonPath("$.displayName").value("テスト太郎"))
            // パスワードハッシュが応答に混入していないこと
            .andExpect(jsonPath("$.passwordHash").doesNotExist())
    }

    @Test
    @DisplayName("メールアドレスは大文字・空白を正規化して重複判定される")
    fun emailIsNormalized() {
        val email = uniqueEmail()

        mockMvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signUpBody(email, "password1234"))
        ).andExpect(status().isCreated)

        // 大文字にしただけの同じアドレスは重複として弾かれる
        mockMvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signUpBody(email.uppercase(), "password1234"))
        )
            .andExpect(status().isConflict)
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"))
    }

    @Test
    @DisplayName("パスワードが違うと 401 になり、原因を区別できるメッセージは返さない")
    fun loginWithWrongPassword() {
        val email = uniqueEmail()

        mockMvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signUpBody(email, "password1234"))
        ).andExpect(status().isCreated)

        val wrongPassword = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"wrong-password"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
            .andReturn()

        val unknownUser = mockMvc.perform(
            post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"${uniqueEmail()}","password":"password1234"}""")
        )
            .andExpect(status().isUnauthorized)
            .andReturn()

        // 存在しないユーザーと誤ったパスワードで応答が同一であること
        assertThat(unknownUser.response.contentAsString)
            .isEqualTo(wrongPassword.response.contentAsString)
    }

    @Test
    @DisplayName("トークンなしで保護エンドポイントを叩くと 401 と共通形式の JSON が返る")
    fun meWithoutToken() {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
            .andExpect(jsonPath("$.message").isNotEmpty)
    }

    @Test
    @DisplayName("リフレッシュで新しいトークンが発行され、使用済みトークンは再利用できない")
    fun refreshRotatesToken() {
        val email = uniqueEmail()

        val signUp = mockMvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signUpBody(email, "password1234"))
        ).andExpect(status().isCreated).andReturn()

        val firstRefreshToken = readField(signUp, "refreshToken")

        val refreshed = mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$firstRefreshToken"}""")
        ).andExpect(status().isOk).andReturn()

        val secondRefreshToken = readField(refreshed, "refreshToken")
        assertThat(secondRefreshToken).isNotEqualTo(firstRefreshToken)

        // ローテーション済みの古いトークンは失効している
        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$firstRefreshToken"}""")
        )
            .andExpect(status().isUnauthorized)
            .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"))

        // 新しいトークンは有効
        mockMvc.perform(
            post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"refreshToken":"$secondRefreshToken"}""")
        ).andExpect(status().isOk)
    }

    @Test
    @DisplayName("ログアウトするとそのリフレッシュトークンは使えなくなる")
    fun logoutRevokesToken() {
        val email = uniqueEmail()

        val signUp = mockMvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(signUpBody(email, "password1234"))
        ).andExpect(status().isCreated).andReturn()

        val refreshToken = readField(signUp, "refreshToken")
        val logoutBody = """{"refreshToken":"$refreshToken"}"""

        mockMvc.perform(
            post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON).content(logoutBody)
        ).andExpect(status().isNoContent)

        mockMvc.perform(
            post("/api/auth/refresh").contentType(MediaType.APPLICATION_JSON).content(logoutBody)
        ).andExpect(status().isUnauthorized)

        // ログアウトは冪等。2 回目もエラーにしない。
        mockMvc.perform(
            post("/api/auth/logout").contentType(MediaType.APPLICATION_JSON).content(logoutBody)
        ).andExpect(status().isNoContent)
    }

    @Test
    @DisplayName("入力検証エラーはフィールドごとの明細つきで 400 を返す")
    fun validationError() {
        mockMvc.perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"not-an-email","password":"short","displayName":""}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
            .andExpect(jsonPath("$.errors").isArray)
            .andExpect(jsonPath("$.errors.length()").value(3))
    }
}
