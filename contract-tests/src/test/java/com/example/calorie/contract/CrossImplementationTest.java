package com.example.calorie.contract;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * 2 つの実装をまたいだ相互運用の検証。
 *
 * <p>両実装は同じ PostgreSQL を共有しているため、片方で作ったデータをもう片方から
 * 扱えるはずである。これが通ることで、次の 3 点が揃っていることを確認できる。
 *
 * <ul>
 *   <li>パスワードハッシュの方式（BCrypt strength 10）が同一</li>
 *   <li>JWT の署名鍵とアルゴリズムが同一</li>
 *   <li>リフレッシュトークンのハッシュ方式（SHA-256）が同一</li>
 * </ul>
 *
 * <p>実運用では署名鍵を実装ごとに分けてよいが、この検証を成立させるため
 * 開発時の既定値は揃えている。
 */
class CrossImplementationTest {

    private static final String DATE = LocalDate.now().toString();

    private static Backend java;
    private static Backend kotlin;

    @BeforeAll
    static void resolveBackends() {
        List<Backend> backends = Backend.fromSystemProperty();

        java = backends.stream().filter(b -> b.name().equals("java")).findFirst().orElse(null);
        kotlin = backends.stream().filter(b -> b.name().equals("kotlin")).findFirst().orElse(null);

        // 片方だけを対象に走らせている場合は、このクラスは検証対象外とする
        assumeTrue(java != null && kotlin != null,
                "相互運用の検証には java と kotlin の両方が必要です");
    }

    @Test
    @DisplayName("Java で登録したユーザーが Kotlin でログインできる（BCrypt の設定が一致している）")
    void userCreatedOnJavaCanLoginOnKotlin() {
        var tokens = java.signUpNewUser();

        var response = kotlin.post("/auth/login", """
                {"email":"%s","password":"%s"}
                """.formatted(tokens.email(), tokens.password()), null);

        assertThat(response.status())
                .as("Kotlin 側でログインできること。失敗する場合は BCrypt の strength を確認する")
                .isEqualTo(200);
        assertThat(response.json().get("accessToken").asText()).isNotBlank();
    }

    @Test
    @DisplayName("Java が発行したアクセストークンを Kotlin が検証できる（署名鍵が一致している）")
    void accessTokenFromJavaWorksOnKotlin() {
        var tokens = java.signUpNewUser();

        var response = kotlin.get("/auth/me", tokens.accessToken());

        assertThat(response.status())
                .as("Kotlin 側で検証できること。失敗する場合は app.jwt.secret を確認する")
                .isEqualTo(200);
        assertThat(response.json().get("email").asText()).isEqualTo(tokens.email());
    }

    @Test
    @DisplayName("Kotlin が発行したアクセストークンを Java が検証できる")
    void accessTokenFromKotlinWorksOnJava() {
        var tokens = kotlin.signUpNewUser();

        var response = java.get("/auth/me", tokens.accessToken());

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().get("email").asText()).isEqualTo(tokens.email());
    }

    @Test
    @DisplayName("Java が発行したリフレッシュトークンを Kotlin でローテーションできる（SHA-256 が一致している）")
    void refreshTokenFromJavaCanBeRotatedOnKotlin() {
        var tokens = java.signUpNewUser();

        var response = kotlin.post("/auth/refresh", """
                {"refreshToken":"%s"}
                """.formatted(tokens.refreshToken()), null);

        assertThat(response.status())
                .as("Kotlin 側で更新できること。失敗する場合はハッシュ方式を確認する")
                .isEqualTo(200);
        assertThat(response.json().get("refreshToken").asText()).isNotEqualTo(tokens.refreshToken());
    }

    @Test
    @DisplayName("Java で記録した食事が Kotlin の集計に反映される（同一 DB を共有している）")
    void mealLoggedOnJavaAppearsInKotlinSummary() {
        var tokens = java.signUpNewUser();
        long riceId = java.findFoodId("ごはん（精白米）", tokens.accessToken());

        var created = java.post("/meal-logs", """
                {"eatenOn":"%s","mealType":"BREAKFAST","items":[{"foodId":%d,"amountG":150}]}
                """.formatted(DATE, riceId), tokens.accessToken());
        assertThat(created.status()).isEqualTo(201);

        var summary = kotlin.get("/summaries/daily?date=" + DATE, tokens.accessToken());

        assertThat(summary.status()).isEqualTo(200);
        assertThat(summary.json().at("/total/kcal").asDouble()).isEqualTo(252.00);
        assertThat(summary.json().at("/byMealType/0/mealType").asText()).isEqualTo("BREAKFAST");
    }

    @Test
    @DisplayName("Kotlin で記録した食事を Java から削除できる")
    void mealLoggedOnKotlinCanBeDeletedOnJava() {
        var tokens = kotlin.signUpNewUser();
        long riceId = kotlin.findFoodId("ごはん（精白米）", tokens.accessToken());

        var created = kotlin.post("/meal-logs", """
                {"eatenOn":"%s","mealType":"DINNER","items":[{"foodId":%d,"amountG":100}]}
                """.formatted(DATE, riceId), tokens.accessToken());
        long mealLogId = created.json().get("id").asLong();

        assertThat(java.delete("/meal-logs/" + mealLogId, tokens.accessToken()).status())
                .isEqualTo(204);
        assertThat(kotlin.get("/meal-logs?date=" + DATE, tokens.accessToken()).json()).isEmpty();
    }

    @Test
    @DisplayName("同じ入力に対する目標算出の結果が両実装で完全に一致する")
    void goalSuggestionIsIdenticalAcrossImplementations() {
        // 同じユーザー・同じプロフィール・同じ体重で、両実装に算出させる
        var tokens = java.signUpNewUser();

        java.put("/profile", """
                {"sex":"FEMALE","birthDate":"1992-03-15","heightCm":162.5,"activityLevel":"MODERATE"}
                """, tokens.accessToken());
        java.put("/body-records/" + DATE, """
                {"weightKg":58.4}
                """, tokens.accessToken());

        var fromJava = java.get("/goals/suggestion?paceKgPerMonth=1.5", tokens.accessToken());
        var fromKotlin = kotlin.get("/goals/suggestion?paceKgPerMonth=1.5", tokens.accessToken());

        assertThat(fromJava.status()).isEqualTo(200);
        assertThat(fromKotlin.status()).isEqualTo(200);

        // 応答本文が 1 バイトも違わないこと。BigDecimal の桁数の扱いまで含めて揃う。
        assertThat(fromKotlin.body())
                .as("目標算出の応答が完全一致すること")
                .isEqualTo(fromJava.body());
    }
}
