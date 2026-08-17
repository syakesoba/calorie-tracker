package com.example.calorie.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 認証フローの結合テスト。
 *
 * <p>H2 ではなく実際の PostgreSQL（calorie_test データベース）に対して実行する。
 * Flyway のマイグレーションと CHECK 制約が本番と同じ条件で効いていることを
 * 確認したいため。
 *
 * <p><b>実行前に {@code docker compose up -d db} で DB を起動しておくこと。</b>
 *
 * <p>各テストは {@code @Transactional} によりロールバックされるため、
 * テスト間でデータは残らない。それでもメールアドレスをユニークにしているのは、
 * ロールバックされない経路が将来混ざったときに原因を追いやすくするため。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthFlowIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private String uniqueEmail() {
        return "user-" + UUID.randomUUID() + "@example.com";
    }

    private String signUpBody(String email, String password) {
        return """
                {"email":"%s","password":"%s","displayName":"テスト太郎"}
                """.formatted(email, password);
    }

    @Test
    @DisplayName("登録するとトークンが返り、そのトークンで自分の情報を取得できる")
    void signUpThenFetchMe() throws Exception {
        String email = uniqueEmail();

        MvcResult signUpResult = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody(email, "password1234")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        String accessToken = readField(signUpResult, "accessToken");

        mockMvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.displayName").value("テスト太郎"))
                // パスワードハッシュが応答に混入していないこと
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("メールアドレスは大文字・空白を正規化して重複判定される")
    void emailIsNormalized() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody(email, "password1234")))
                .andExpect(status().isCreated());

        // 大文字にしただけの同じアドレスは重複として弾かれる
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody(email.toUpperCase(), "password1234")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_USED"));
    }

    @Test
    @DisplayName("パスワードが違うと 401 になり、原因を区別できるメッセージは返さない")
    void loginWithWrongPassword() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody(email, "password1234")))
                .andExpect(status().isCreated());

        String wrongPasswordBody = """
                {"email":"%s","password":"wrong-password"}
                """.formatted(email);

        MvcResult wrongPassword = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(wrongPasswordBody))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                .andReturn();

        String unknownUserBody = """
                {"email":"%s","password":"password1234"}
                """.formatted(uniqueEmail());

        MvcResult unknownUser = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(unknownUserBody))
                .andExpect(status().isUnauthorized())
                .andReturn();

        // 存在しないユーザーと誤ったパスワードで応答が同一であること
        assertThat(unknownUser.getResponse().getContentAsString())
                .isEqualTo(wrongPassword.getResponse().getContentAsString());
    }

    @Test
    @DisplayName("トークンなしで保護エンドポイントを叩くと 401 と共通形式の JSON が返る")
    void meWithoutToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("リフレッシュで新しいトークンが発行され、使用済みトークンは再利用できない")
    void refreshRotatesToken() throws Exception {
        String email = uniqueEmail();

        MvcResult signUp = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody(email, "password1234")))
                .andExpect(status().isCreated())
                .andReturn();

        String firstRefreshToken = readField(signUp, "refreshToken");

        MvcResult refreshed = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(firstRefreshToken)))
                .andExpect(status().isOk())
                .andReturn();

        String secondRefreshToken = readField(refreshed, "refreshToken");
        assertThat(secondRefreshToken).isNotEqualTo(firstRefreshToken);

        // ローテーション済みの古いトークンは失効している
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(firstRefreshToken)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // 新しいトークンは有効
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(secondRefreshToken)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("ログアウトするとそのリフレッシュトークンは使えなくなる")
    void logoutRevokesToken() throws Exception {
        String email = uniqueEmail();

        MvcResult signUp = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(signUpBody(email, "password1234")))
                .andExpect(status().isCreated())
                .andReturn();

        String refreshToken = readField(signUp, "refreshToken");
        String logoutBody = """
                {"refreshToken":"%s"}
                """.formatted(refreshToken);

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logoutBody))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logoutBody))
                .andExpect(status().isUnauthorized());

        // ログアウトは冪等。2 回目もエラーにしない。
        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(logoutBody))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("入力検証エラーはフィールドごとの明細つきで 400 を返す")
    void validationError() throws Exception {
        mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"short","displayName":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors").isArray())
                .andExpect(jsonPath("$.errors.length()").value(3));
    }

    private String readField(MvcResult result, String field) throws Exception {
        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        return json.get(field).asText();
    }
}
