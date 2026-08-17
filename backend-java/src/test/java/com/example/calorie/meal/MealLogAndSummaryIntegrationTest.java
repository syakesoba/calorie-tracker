package com.example.calorie.meal;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 食事記録から日別集計までの結合テスト。
 *
 * <p>実行前に {@code docker compose up -d db} で DB を起動しておくこと。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MealLogAndSummaryIntegrationTest {

    private static final String DATE = "2026-08-17";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ObjectMapper objectMapper;

    private String token;
    private long riceFoodId;

    @BeforeEach
    void setUp() throws Exception {
        token = signUpAndGetToken();
        riceFoodId = findFoodId("ごはん（精白米）");
    }

    // ------------------------------------------------------------------ 記録

    @Test
    @DisplayName("食品IDと分量を送ると、サーバー側で換算した栄養値が記録される")
    void createMealLogCalculatesNutrition() throws Exception {
        // ごはん 168kcal/100g を 150g → 252kcal
        mockMvc.perform(auth(post("/api/meal-logs"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eatenOn":"%s","mealType":"BREAKFAST",
                                 "items":[{"foodId":%d,"amountG":150}]}
                                """.formatted(DATE, riceFoodId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.items[0].foodName").value("ごはん（精白米）"))
                .andExpect(jsonPath("$.items[0].nutrition.kcal").value(252.00))
                .andExpect(jsonPath("$.total.kcal").value(252.00));
    }

    @Test
    @DisplayName("栄養値はスナップショットとして保存され、クライアントの申告値は使われない")
    void nutritionIsServerCalculated() throws Exception {
        // クライアントが kcal を偽装して送っても、リクエストの形に無いので無視される。
        // 送られてきた値ではなくマスタから計算した値が入ることを確認する。
        MvcResult result = mockMvc.perform(auth(post("/api/meal-logs"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eatenOn":"%s","mealType":"LUNCH",
                                 "items":[{"foodId":%d,"amountG":100,"kcal":1}]}
                                """.formatted(DATE, riceFoodId)))
                .andExpect(status().isCreated())
                .andReturn();

        JsonNode json = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(json.at("/items/0/nutrition/kcal").asDouble()).isEqualTo(168.00);
    }

    @Test
    @DisplayName("存在しない食品を指定すると 404 になり、記録は作られない")
    void unknownFoodIsRejected() throws Exception {
        mockMvc.perform(auth(post("/api/meal-logs"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eatenOn":"%s","mealType":"DINNER",
                                 "items":[{"foodId":999999,"amountG":100}]}
                                """.formatted(DATE)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FOOD_NOT_FOUND"));

        mockMvc.perform(auth(get("/api/meal-logs")).param("date", DATE))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("品目が空の記録は 400 で弾かれる")
    void emptyItemsRejected() throws Exception {
        mockMvc.perform(auth(post("/api/meal-logs"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eatenOn":"%s","mealType":"SNACK","items":[]}
                                """.formatted(DATE)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ------------------------------------------------------------------ 集計

    @Test
    @DisplayName("複数の食事が食事区分ごとに内訳として集計される")
    void dailySummaryAggregatesByMealType() throws Exception {
        createMealLog("BREAKFAST", riceFoodId, 100);   // 168 kcal
        createMealLog("DINNER", riceFoodId, 200);      // 336 kcal

        mockMvc.perform(auth(get("/api/summaries/daily")).param("date", DATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total.kcal").value(504.00))
                .andExpect(jsonPath("$.byMealType.length()").value(2))
                // EnumMap により、宣言順（朝食 → 夕食）で返る
                .andExpect(jsonPath("$.byMealType[0].mealType").value("BREAKFAST"))
                .andExpect(jsonPath("$.byMealType[1].mealType").value("DINNER"));
    }

    @Test
    @DisplayName("目標が未設定でも集計は返り、目標と残量だけが null になる")
    void summaryWorksWithoutGoal() throws Exception {
        createMealLog("BREAKFAST", riceFoodId, 100);

        mockMvc.perform(auth(get("/api/summaries/daily")).param("date", DATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total.kcal").value(168.00))
                .andExpect(jsonPath("$.goal").doesNotExist())
                .andExpect(jsonPath("$.remaining").doesNotExist());
    }

    @Test
    @DisplayName("目標を超過すると残量が負の値になる")
    void remainingGoesNegativeWhenOverGoal() throws Exception {
        putGoal(500);
        createMealLog("DINNER", riceFoodId, 500);   // 840 kcal

        mockMvc.perform(auth(get("/api/summaries/daily")).param("date", DATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.goal.targetKcal").value(500))
                .andExpect(jsonPath("$.remaining.kcal").value(-340.00));
    }

    @Test
    @DisplayName("期間集計は記録の無い日も 0 kcal で埋めて返す")
    void rangeSummaryFillsMissingDays() throws Exception {
        createMealLog("BREAKFAST", riceFoodId, 100);

        mockMvc.perform(auth(get("/api/summaries/range"))
                        .param("from", "2026-08-15").param("to", "2026-08-17"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(3))
                .andExpect(jsonPath("$.days[0].kcal").value(0))
                .andExpect(jsonPath("$.days[2].date").value(DATE))
                .andExpect(jsonPath("$.days[2].kcal").value(168.00));
    }

    @Test
    @DisplayName("体重の移動平均は記録のある日だけで計算され、空白日は線を途切れさせない")
    void weightMovingAverageSkipsMissingDays() throws Exception {
        putWeight("2026-08-15", "70.0");
        // 8/16 は記録なし
        putWeight("2026-08-17", "72.0");

        MvcResult result = mockMvc.perform(auth(get("/api/summaries/range"))
                        .param("from", "2026-08-15").param("to", "2026-08-17"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode days = objectMapper.readTree(result.getResponse().getContentAsString()).get("days");

        assertThat(days.get(0).get("weightMovingAvgKg").asDouble()).isEqualTo(70.00);
        // 記録の無い 8/16 も、直前までの平均を保って線が途切れない
        assertThat(days.get(1).get("weightKg").isNull()).isTrue();
        assertThat(days.get(1).get("weightMovingAvgKg").asDouble()).isEqualTo(70.00);
        // (70 + 72) / 2 = 71。空白日を 0 で埋めていたら 47.33 になる
        assertThat(days.get(2).get("weightMovingAvgKg").asDouble()).isEqualTo(71.00);
    }

    @Test
    @DisplayName("期間が逆転していると 400 になる")
    void invalidRangeRejected() throws Exception {
        mockMvc.perform(auth(get("/api/summaries/range"))
                        .param("from", "2026-08-17").param("to", "2026-08-15"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_RANGE"));
    }

    // ------------------------------------------------------- 他ユーザーとの分離

    @Test
    @DisplayName("他人の食事記録は取得できず、削除しようとしても 404 になる")
    void cannotReachOtherUsersMealLog() throws Exception {
        MvcResult created = createMealLog("BREAKFAST", riceFoodId, 100);
        long mealLogId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();

        String otherToken = signUpAndGetToken();

        // 一覧には現れない
        mockMvc.perform(get("/api/meal-logs").header("Authorization", "Bearer " + otherToken)
                        .param("date", DATE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        // ID を直接指定しても、403 ではなく 404。
        // 403 だと「その ID は存在する」ことを教えてしまうため。
        mockMvc.perform(delete("/api/meal-logs/" + mealLogId)
                        .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());

        // 本来の持ち主からはまだ見える（削除されていない）
        mockMvc.perform(auth(get("/api/meal-logs")).param("date", DATE))
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("他人が登録した独自食品は検索にも記録にも使えない")
    void cannotUseOtherUsersFood() throws Exception {
        MvcResult created = mockMvc.perform(auth(post("/api/foods"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"自家製プロテインバー",
                                 "nutritionPer100g":{"kcal":350,"proteinG":30,"fatG":10,"carbG":35}}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        long foodId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();

        String otherToken = signUpAndGetToken();

        mockMvc.perform(get("/api/foods").header("Authorization", "Bearer " + otherToken)
                        .param("query", "自家製"))
                .andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(post("/api/meal-logs").header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eatenOn":"%s","mealType":"SNACK",
                                 "items":[{"foodId":%d,"amountG":50}]}
                                """.formatted(DATE, foodId)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("FOOD_NOT_FOUND"));
    }

    // ------------------------------------------------------------------ 食品

    @Test
    @DisplayName("シードデータが検索でき、出所が SEED であることが分かる")
    void seedFoodsAreSearchable() throws Exception {
        mockMvc.perform(auth(get("/api/foods")).param("query", "鶏"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].source").value("SEED"))
                .andExpect(jsonPath("$[0].nutritionPer100g.kcal").isNumber());
    }

    @Test
    @DisplayName("自分で登録した食品は共有マスタより先に並ぶ")
    void ownFoodsRankFirst() throws Exception {
        mockMvc.perform(auth(post("/api/foods"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"特製ごはん大盛り",
                                 "nutritionPer100g":{"kcal":170,"proteinG":3,"fatG":0.5,"carbG":38}}
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(auth(get("/api/foods")).param("query", "ごはん"))
                .andExpect(jsonPath("$[0].source").value("USER"))
                .andExpect(jsonPath("$[0].name").value("特製ごはん大盛り"));
    }

    // ---------------------------------------------------------------- ヘルパー

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder builder) {
        return builder.header("Authorization", "Bearer " + token);
    }

    private String signUpAndGetToken() throws Exception {
        String email = "meal-" + UUID.randomUUID() + "@example.com";
        MvcResult result = mockMvc.perform(post("/api/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password1234","displayName":"テスト"}
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private long findFoodId(String name) throws Exception {
        MvcResult result = mockMvc.perform(auth(get("/api/foods")).param("query", name))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode foods = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(foods).as("シードデータに「%s」が存在すること", name).isNotEmpty();
        return foods.get(0).get("id").asLong();
    }

    private MvcResult createMealLog(String mealType, long foodId, int amountG) throws Exception {
        return mockMvc.perform(auth(post("/api/meal-logs"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eatenOn":"%s","mealType":"%s",
                                 "items":[{"foodId":%d,"amountG":%d}]}
                                """.formatted(DATE, mealType, foodId, amountG)))
                .andExpect(status().isCreated())
                .andReturn();
    }

    private void putGoal(int targetKcal) throws Exception {
        mockMvc.perform(auth(put("/api/goals"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startOn":"2026-08-01","targetKcal":%d,
                                 "targetProteinG":100,"targetFatG":55,"targetCarbG":275}
                                """.formatted(targetKcal)))
                .andExpect(status().isOk());
    }

    private void putWeight(String date, String weightKg) throws Exception {
        mockMvc.perform(auth(put("/api/body-records/" + date))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"weightKg\":%s}".formatted(weightKg)))
                .andExpect(status().isOk());
    }
}
