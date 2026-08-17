package com.example.calorie.contract;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 契約テスト。Java 実装と Kotlin 実装の両方に対して同一の検証を流す。
 *
 * <p>各テストは <b>両方のバックエンドで個別に実行される</b>。片方だけ落ちた場合、
 * JUnit の表示名にどちらかが出るので原因の切り分けができる。
 *
 * <h2>実行の前提</h2>
 * <ul>
 *   <li>両方のバックエンドが起動していること</li>
 *   <li>DB が起動し、Flyway のマイグレーションが適用済みであること</li>
 *   <li>両実装の {@code app.jwt.secret} が同じ値であること（相互運用の検証のため）</li>
 * </ul>
 */
class ApiContractTest {

    private static final LocalDate TODAY = LocalDate.now();
    private static final String DATE = TODAY.toString();

    /** JUnit のパラメータ源。検証対象のバックエンド一覧。 */
    static List<Backend> backends() {
        return Backend.fromSystemProperty();
    }

    // ================================================================== 認証

    @ParameterizedTest(name = "[{0}] 登録すると 201 とトークン一式が返る")
    @MethodSource("backends")
    void signUpReturnsTokens(Backend backend) {
        var tokens = backend.signUpNewUser();

        assertThat(tokens.accessToken()).isNotBlank();
        assertThat(tokens.refreshToken()).isNotBlank();

        // 応答のキー構成が一致していること。片方に余分なキーがあると
        // 生成クライアントの型が両対応できなくなる。
        var response = backend.post("/auth/login", """
                {"email":"%s","password":"%s"}
                """.formatted(tokens.email(), tokens.password()), null);

        assertThat(response.status()).isEqualTo(200);
        assertThat(Backend.sortedFieldNames(response.json()))
                .containsExactly("accessToken", "expiresIn", "refreshToken", "tokenType");
        assertThat(response.json().get("tokenType").asText()).isEqualTo("Bearer");
    }

    @ParameterizedTest(name = "[{0}] /me はパスワードハッシュを含まない")
    @MethodSource("backends")
    void meDoesNotLeakPasswordHash(Backend backend) {
        var tokens = backend.signUpNewUser();

        var response = backend.get("/auth/me", tokens.accessToken());

        assertThat(response.status()).isEqualTo(200);
        assertThat(Backend.sortedFieldNames(response.json()))
                .containsExactly("createdAt", "displayName", "email", "id");
    }

    @ParameterizedTest(name = "[{0}] 未認証の保護エンドポイントは 401 UNAUTHORIZED")
    @MethodSource("backends")
    void unauthenticatedIsRejected(Backend backend) {
        var response = backend.get("/auth/me", null);

        assertThat(response.status()).isEqualTo(401);
        assertThat(response.errorCode()).isEqualTo("UNAUTHORIZED");
        assertThat(response.json().get("message").asText()).isNotBlank();
    }

    @ParameterizedTest(name = "[{0}] メール重複は 409 EMAIL_ALREADY_USED")
    @MethodSource("backends")
    void duplicateEmailIsConflict(Backend backend) {
        var tokens = backend.signUpNewUser();

        // 大文字にしただけの同じアドレスも重複として扱われる（正規化の確認）
        var response = backend.post("/auth/signup", """
                {"email":"%s","password":"%s","displayName":"重複"}
                """.formatted(tokens.email().toUpperCase(), Backend.DEFAULT_PASSWORD), null);

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.errorCode()).isEqualTo("EMAIL_ALREADY_USED");
    }

    @ParameterizedTest(name = "[{0}] 認証失敗はユーザーの存在有無で区別できない")
    @MethodSource("backends")
    void loginFailureIsIndistinguishable(Backend backend) {
        var tokens = backend.signUpNewUser();

        var wrongPassword = backend.post("/auth/login", """
                {"email":"%s","password":"definitely-wrong"}
                """.formatted(tokens.email()), null);

        var unknownUser = backend.post("/auth/login", """
                {"email":"nobody-%s@example.com","password":"%s"}
                """.formatted(java.util.UUID.randomUUID(), Backend.DEFAULT_PASSWORD), null);

        assertThat(wrongPassword.status()).isEqualTo(401);
        assertThat(wrongPassword.errorCode()).isEqualTo("INVALID_CREDENTIALS");
        // 本文が完全に一致すること。メールアドレスの存在推測を防ぐ。
        assertThat(unknownUser.status()).isEqualTo(wrongPassword.status());
        assertThat(unknownUser.body()).isEqualTo(wrongPassword.body());
    }

    @ParameterizedTest(name = "[{0}] 入力検証エラーは 400 VALIDATION_ERROR と明細を返す")
    @MethodSource("backends")
    void validationErrorShape(Backend backend) {
        var response = backend.post("/auth/signup", """
                {"email":"not-an-email","password":"short","displayName":""}
                """, null);

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.errorCode()).isEqualTo("VALIDATION_ERROR");

        JsonNode errors = response.json().get("errors");
        assertThat(errors.isArray()).isTrue();
        assertThat(errors).hasSize(3);
        // 明細の形も揃っていること
        assertThat(Backend.sortedFieldNames(errors.get(0))).containsExactly("field", "message");
    }

    @ParameterizedTest(name = "[{0}] リフレッシュはローテーションし、使用済みは 401")
    @MethodSource("backends")
    void refreshRotates(Backend backend) {
        var tokens = backend.signUpNewUser();
        String body = """
                {"refreshToken":"%s"}
                """.formatted(tokens.refreshToken());

        var refreshed = backend.post("/auth/refresh", body, null);
        assertThat(refreshed.status()).isEqualTo(200);

        String newRefreshToken = refreshed.json().get("refreshToken").asText();
        assertThat(newRefreshToken).isNotEqualTo(tokens.refreshToken());

        var reused = backend.post("/auth/refresh", body, null);
        assertThat(reused.status()).isEqualTo(401);
        assertThat(reused.errorCode()).isEqualTo("INVALID_REFRESH_TOKEN");
    }

    @ParameterizedTest(name = "[{0}] ログアウトは冪等で 204 を返す")
    @MethodSource("backends")
    void logoutIsIdempotent(Backend backend) {
        var tokens = backend.signUpNewUser();
        String body = """
                {"refreshToken":"%s"}
                """.formatted(tokens.refreshToken());

        assertThat(backend.post("/auth/logout", body, null).status()).isEqualTo(204);
        assertThat(backend.post("/auth/logout", body, null).status()).isEqualTo(204);
        // 失効後は更新できない
        assertThat(backend.post("/auth/refresh", body, null).status()).isEqualTo(401);
    }

    // ================================================================== 食品

    @ParameterizedTest(name = "[{0}] 食品検索の応答形が一致する")
    @MethodSource("backends")
    void foodSearchShape(Backend backend) {
        var tokens = backend.signUpNewUser();

        var response = backend.get("/foods?query=" + urlEncode("鶏"), tokens.accessToken());

        assertThat(response.status()).isEqualTo(200);
        JsonNode first = response.json().get(0);
        assertThat(Backend.sortedFieldNames(first))
                .containsExactly("category", "id", "name", "nameKana", "nutritionPer100g", "source");
        assertThat(first.get("source").asText()).isEqualTo("SEED");
        assertThat(Backend.sortedFieldNames(first.get("nutritionPer100g")))
                .containsExactly("carbG", "fatG", "fiberG", "kcal", "proteinG", "saltG", "sugarG");
    }

    @ParameterizedTest(name = "[{0}] 他人が登録した独自食品は参照できない")
    @MethodSource("backends")
    void otherUsersFoodIsInvisible(Backend backend) {
        var owner = backend.signUpNewUser();
        var created = backend.post("/foods", """
                {"name":"契約テスト用プロテインバー",
                 "nutritionPer100g":{"kcal":350,"proteinG":30,"fatG":10,"carbG":35}}
                """, owner.accessToken());
        assertThat(created.status()).isEqualTo(201);
        long foodId = created.json().get("id").asLong();

        var other = backend.signUpNewUser();

        var search = backend.get("/foods?query=" + urlEncode("契約テスト用"), other.accessToken());
        assertThat(search.json()).isEmpty();

        var log = backend.post("/meal-logs", """
                {"eatenOn":"%s","mealType":"SNACK","items":[{"foodId":%d,"amountG":50}]}
                """.formatted(DATE, foodId), other.accessToken());
        assertThat(log.status()).isEqualTo(404);
        assertThat(log.errorCode()).isEqualTo("FOOD_NOT_FOUND");
    }

    // ============================================================== 食事記録

    @ParameterizedTest(name = "[{0}] 栄養値はサーバーが換算して確定する")
    @MethodSource("backends")
    void nutritionIsServerCalculated(Backend backend) {
        var tokens = backend.signUpNewUser();
        long riceId = backend.findFoodId("ごはん（精白米）", tokens.accessToken());

        // クライアントが kcal を偽装して送っても、リクエストの形に無いので無視される
        var response = backend.post("/meal-logs", """
                {"eatenOn":"%s","mealType":"BREAKFAST",
                 "items":[{"foodId":%d,"amountG":150,"kcal":1}]}
                """.formatted(DATE, riceId), tokens.accessToken());

        assertThat(response.status()).isEqualTo(201);
        // 168 kcal/100g × 150g = 252.00
        assertThat(response.json().at("/items/0/nutrition/kcal").asDouble()).isEqualTo(252.00);
        assertThat(response.json().at("/total/kcal").asDouble()).isEqualTo(252.00);
        assertThat(response.json().at("/items/0/foodName").asText()).isEqualTo("ごはん（精白米）");
    }

    @ParameterizedTest(name = "[{0}] 存在しない食品は 404 FOOD_NOT_FOUND")
    @MethodSource("backends")
    void unknownFoodIsNotFound(Backend backend) {
        var tokens = backend.signUpNewUser();

        var response = backend.post("/meal-logs", """
                {"eatenOn":"%s","mealType":"DINNER","items":[{"foodId":99999999,"amountG":100}]}
                """.formatted(DATE), tokens.accessToken());

        assertThat(response.status()).isEqualTo(404);
        assertThat(response.errorCode()).isEqualTo("FOOD_NOT_FOUND");
    }

    @ParameterizedTest(name = "[{0}] 品目が空の記録は 400 で弾かれる")
    @MethodSource("backends")
    void emptyItemsRejected(Backend backend) {
        var tokens = backend.signUpNewUser();

        var response = backend.post("/meal-logs", """
                {"eatenOn":"%s","mealType":"SNACK","items":[]}
                """.formatted(DATE), tokens.accessToken());

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.errorCode()).isEqualTo("VALIDATION_ERROR");
    }

    @ParameterizedTest(name = "[{0}] 他人の記録は 403 ではなく 404")
    @MethodSource("backends")
    void otherUsersMealLogIsNotFound(Backend backend) {
        var owner = backend.signUpNewUser();
        long riceId = backend.findFoodId("ごはん（精白米）", owner.accessToken());

        var created = backend.post("/meal-logs", """
                {"eatenOn":"%s","mealType":"LUNCH","items":[{"foodId":%d,"amountG":100}]}
                """.formatted(DATE, riceId), owner.accessToken());
        long mealLogId = created.json().get("id").asLong();

        var other = backend.signUpNewUser();

        // 一覧には現れない
        assertThat(backend.get("/meal-logs?date=" + DATE, other.accessToken()).json()).isEmpty();

        // ID 直指定でも 404。403 だと「その ID は存在する」ことを教えてしまう。
        assertThat(backend.delete("/meal-logs/" + mealLogId, other.accessToken()).status())
                .isEqualTo(404);

        // 本来の持ち主からはまだ見える（削除されていない）
        assertThat(backend.get("/meal-logs?date=" + DATE, owner.accessToken()).json()).hasSize(1);
    }

    // ================================================================== 集計

    @ParameterizedTest(name = "[{0}] 目標未設定でもキーは存在し、値だけが null")
    @MethodSource("backends")
    void summaryKeepsNullKeys(Backend backend) {
        var tokens = backend.signUpNewUser();
        long riceId = backend.findFoodId("ごはん（精白米）", tokens.accessToken());
        backend.post("/meal-logs", """
                {"eatenOn":"%s","mealType":"BREAKFAST","items":[{"foodId":%d,"amountG":100}]}
                """.formatted(DATE, riceId), tokens.accessToken());

        var response = backend.get("/summaries/daily?date=" + DATE, tokens.accessToken());

        assertThat(response.status()).isEqualTo(200);
        assertThat(response.json().at("/total/kcal").asDouble()).isEqualTo(168.00);

        // キーごと消えると「値が null」と「キーが無い」の区別がクライアントに
        // 持ち込まれる。Phase 1 の検証でこの差異を踏んだため明示的に確認する。
        assertThat(response.hasKey("goal")).as("goal キーが存在する").isTrue();
        assertThat(response.isNull("goal")).as("goal が null").isTrue();
        assertThat(response.hasKey("remaining")).as("remaining キーが存在する").isTrue();
        assertThat(response.isNull("remaining")).as("remaining が null").isTrue();
        assertThat(response.hasKey("weightKg")).as("weightKg キーが存在する").isTrue();
    }

    @ParameterizedTest(name = "[{0}] 目標を超過すると残量が負になる")
    @MethodSource("backends")
    void remainingGoesNegative(Backend backend) {
        var tokens = backend.signUpNewUser();
        long riceId = backend.findFoodId("ごはん（精白米）", tokens.accessToken());

        var goal = backend.put("/goals", """
                {"startOn":"%s","targetKcal":500,
                 "targetProteinG":100,"targetFatG":55,"targetCarbG":275}
                """.formatted(DATE), tokens.accessToken());
        assertThat(goal.status()).isEqualTo(200);

        backend.post("/meal-logs", """
                {"eatenOn":"%s","mealType":"DINNER","items":[{"foodId":%d,"amountG":500}]}
                """.formatted(DATE, riceId), tokens.accessToken());

        var response = backend.get("/summaries/daily?date=" + DATE, tokens.accessToken());

        // 840 − 500 = −340。0 で切り上げると超過量が分からなくなる。
        assertThat(response.json().at("/remaining/kcal").asDouble()).isEqualTo(-340.00);
        assertThat(response.json().at("/goal/targetKcal").asInt()).isEqualTo(500);
    }

    @ParameterizedTest(name = "[{0}] 期間集計は記録の無い日も 0 で埋める")
    @MethodSource("backends")
    void rangeFillsMissingDays(Backend backend) {
        var tokens = backend.signUpNewUser();
        long riceId = backend.findFoodId("ごはん（精白米）", tokens.accessToken());
        backend.post("/meal-logs", """
                {"eatenOn":"%s","mealType":"BREAKFAST","items":[{"foodId":%d,"amountG":100}]}
                """.formatted(DATE, riceId), tokens.accessToken());

        String from = TODAY.minusDays(2).toString();
        var response = backend.get("/summaries/range?from=" + from + "&to=" + DATE, tokens.accessToken());

        assertThat(response.status()).isEqualTo(200);
        JsonNode days = response.json().get("days");
        assertThat(days).hasSize(3);
        assertThat(days.get(0).get("kcal").asDouble()).isEqualTo(0.0);
        assertThat(days.get(2).get("date").asText()).isEqualTo(DATE);
        assertThat(days.get(2).get("kcal").asDouble()).isEqualTo(168.00);
        assertThat(Backend.sortedFieldNames(days.get(0))).containsExactly(
                "carbG", "date", "fatG", "kcal", "proteinG",
                "targetKcal", "weightKg", "weightMovingAvgKg");
    }

    @ParameterizedTest(name = "[{0}] 体重の移動平均は空白日を 0 扱いしない")
    @MethodSource("backends")
    void movingAverageSkipsGaps(Backend backend) {
        var tokens = backend.signUpNewUser();
        String twoDaysAgo = TODAY.minusDays(2).toString();

        backend.put("/body-records/" + twoDaysAgo, """
                {"weightKg":70.0}
                """, tokens.accessToken());
        // 前日は記録なし
        backend.put("/body-records/" + DATE, """
                {"weightKg":72.0}
                """, tokens.accessToken());

        var response = backend.get(
                "/summaries/range?from=" + twoDaysAgo + "&to=" + DATE, tokens.accessToken());
        JsonNode days = response.json().get("days");

        assertThat(days.get(0).get("weightMovingAvgKg").asDouble()).isEqualTo(70.00);
        // 記録の無い日も直前までの平均を保ち、線が途切れない
        assertThat(days.get(1).get("weightKg").isNull()).isTrue();
        assertThat(days.get(1).get("weightMovingAvgKg").asDouble()).isEqualTo(70.00);
        // (70 + 72) / 2 = 71。空白日を 0 で埋めていたら 47.33 になる。
        assertThat(days.get(2).get("weightMovingAvgKg").asDouble()).isEqualTo(71.00);
    }

    @ParameterizedTest(name = "[{0}] 期間が逆転していると 400 INVALID_RANGE")
    @MethodSource("backends")
    void invalidRangeRejected(Backend backend) {
        var tokens = backend.signUpNewUser();

        var response = backend.get(
                "/summaries/range?from=" + DATE + "&to=" + TODAY.minusDays(2), tokens.accessToken());

        assertThat(response.status()).isEqualTo(400);
        assertThat(response.errorCode()).isEqualTo("INVALID_RANGE");
    }

    // ========================================================== 目標算出

    @ParameterizedTest(name = "[{0}] プロフィール未設定の目標算出は 409")
    @MethodSource("backends")
    void goalSuggestionRequiresProfile(Backend backend) {
        var tokens = backend.signUpNewUser();

        var response = backend.get("/goals/suggestion?paceKgPerMonth=2", tokens.accessToken());

        assertThat(response.status()).isEqualTo(409);
        assertThat(response.errorCode()).isEqualTo("PROFILE_REQUIRED");
    }

    @ParameterizedTest(name = "[{0}] 目標算出の数値が一致する")
    @MethodSource("backends")
    void goalSuggestionValues(Backend backend) {
        var tokens = backend.signUpNewUser();

        backend.put("/profile", """
                {"sex":"MALE","birthDate":"1996-01-01","heightCm":175,"activityLevel":"SEDENTARY"}
                """, tokens.accessToken());
        backend.put("/body-records/" + DATE, """
                {"weightKg":70.0}
                """, tokens.accessToken());

        var response = backend.get("/goals/suggestion?paceKgPerMonth=0", tokens.accessToken());

        assertThat(response.status()).isEqualTo(200);
        assertThat(Backend.sortedFieldNames(response.json())).containsExactly(
                "basedOnWeightKg", "bmr", "cappedAtBmr", "paceKgPerMonth",
                "targetCarbG", "targetFatG", "targetKcal", "targetProteinG", "tdee");

        // 生年 1996-01-01 なので、今日時点の年齢から BMR が決まる。
        // 両実装で同じ年齢計算・同じ式を使っているので、値も一致するはず。
        int bmr = response.json().get("bmr").asInt();
        assertThat(bmr).isGreaterThan(1000);
        // 座位（×1.2）でペース 0 なら 目標 = TDEE
        assertThat(response.json().get("targetKcal").asInt())
                .isEqualTo(response.json().get("tdee").asInt());
        assertThat(response.json().get("cappedAtBmr").asBoolean()).isFalse();
    }

    @ParameterizedTest(name = "[{0}] 極端な減量ペースは基礎代謝で打ち切られる")
    @MethodSource("backends")
    void goalSuggestionCapsAtBmr(Backend backend) {
        var tokens = backend.signUpNewUser();

        backend.put("/profile", """
                {"sex":"MALE","birthDate":"1996-01-01","heightCm":175,"activityLevel":"SEDENTARY"}
                """, tokens.accessToken());
        backend.put("/body-records/" + DATE, """
                {"weightKg":70.0}
                """, tokens.accessToken());

        var response = backend.get("/goals/suggestion?paceKgPerMonth=4", tokens.accessToken());

        assertThat(response.json().get("cappedAtBmr").asBoolean()).isTrue();
        assertThat(response.json().get("targetKcal").asInt())
                .isEqualTo(response.json().get("bmr").asInt());
    }

    private static String urlEncode(String value) {
        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
    }
}
