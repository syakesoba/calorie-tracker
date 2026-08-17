package com.example.calorie.meal

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.MvcResult
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * 食事記録から日別集計までの結合テスト。
 *
 * **Java 実装の MealLogAndSummaryIntegrationTest と同じケースを並べている。**
 *
 * 実行前に `docker compose up -d db` で DB を起動しておくこと。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class MealLogAndSummaryIntegrationTest {

    private companion object {
        const val DATE = "2026-08-17"
    }

    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    private lateinit var token: String
    private var riceFoodId: Long = 0

    @BeforeEach
    fun setUp() {
        token = signUpAndGetToken()
        riceFoodId = findFoodId("ごはん（精白米）")
    }

    // ------------------------------------------------------------------ 記録

    @Test
    @DisplayName("食品IDと分量を送ると、サーバー側で換算した栄養値が記録される")
    fun createMealLogCalculatesNutrition() {
        // ごはん 168kcal/100g を 150g → 252kcal
        mockMvc.perform(
            auth(post("/api/meal-logs")).contentType(MediaType.APPLICATION_JSON).content(
                """{"eatenOn":"$DATE","mealType":"BREAKFAST",
                    "items":[{"foodId":$riceFoodId,"amountG":150}]}"""
            )
        )
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.items[0].foodName").value("ごはん（精白米）"))
            .andExpect(jsonPath("$.items[0].nutrition.kcal").value(252.00))
            .andExpect(jsonPath("$.total.kcal").value(252.00))
    }

    @Test
    @DisplayName("栄養値はスナップショットとして保存され、クライアントの申告値は使われない")
    fun nutritionIsServerCalculated() {
        val result = mockMvc.perform(
            auth(post("/api/meal-logs")).contentType(MediaType.APPLICATION_JSON).content(
                """{"eatenOn":"$DATE","mealType":"LUNCH",
                    "items":[{"foodId":$riceFoodId,"amountG":100,"kcal":1}]}"""
            )
        ).andExpect(status().isCreated).andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)
        assertThat(json.at("/items/0/nutrition/kcal").asDouble()).isEqualTo(168.00)
    }

    @Test
    @DisplayName("存在しない食品を指定すると 404 になり、記録は作られない")
    fun unknownFoodIsRejected() {
        mockMvc.perform(
            auth(post("/api/meal-logs")).contentType(MediaType.APPLICATION_JSON).content(
                """{"eatenOn":"$DATE","mealType":"DINNER",
                    "items":[{"foodId":999999,"amountG":100}]}"""
            )
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("FOOD_NOT_FOUND"))

        mockMvc.perform(auth(get("/api/meal-logs")).param("date", DATE))
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    @DisplayName("品目が空の記録は 400 で弾かれる")
    fun emptyItemsRejected() {
        mockMvc.perform(
            auth(post("/api/meal-logs")).contentType(MediaType.APPLICATION_JSON)
                .content("""{"eatenOn":"$DATE","mealType":"SNACK","items":[]}""")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
    }

    // ------------------------------------------------------------------ 集計

    @Test
    @DisplayName("複数の食事が食事区分ごとに内訳として集計される")
    fun dailySummaryAggregatesByMealType() {
        createMealLog("BREAKFAST", riceFoodId, 100)   // 168 kcal
        createMealLog("DINNER", riceFoodId, 200)      // 336 kcal

        mockMvc.perform(auth(get("/api/summaries/daily")).param("date", DATE))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total.kcal").value(504.00))
            .andExpect(jsonPath("$.byMealType.length()").value(2))
            // EnumMap により、宣言順（朝食 → 夕食）で返る
            .andExpect(jsonPath("$.byMealType[0].mealType").value("BREAKFAST"))
            .andExpect(jsonPath("$.byMealType[1].mealType").value("DINNER"))
    }

    @Test
    @DisplayName("目標が未設定でも集計は返り、目標と残量はキーを保ったまま null になる")
    fun summaryWorksWithoutGoal() {
        createMealLog("BREAKFAST", riceFoodId, 100)

        val result = mockMvc.perform(auth(get("/api/summaries/daily")).param("date", DATE))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.total.kcal").value(168.00))
            .andReturn()

        val json = objectMapper.readTree(result.response.contentAsString)

        // キーごと消すのではなく、キーは存在して値が null であること。
        // JsonPath の exists() は明示的な null を「値なし」と扱うので、生 JSON で確認する。
        assertThat(json.has("goal")).`as`("goal キーが存在すること").isTrue()
        assertThat(json.get("goal").isNull).`as`("goal が null であること").isTrue()
        assertThat(json.has("remaining")).`as`("remaining キーが存在すること").isTrue()
        assertThat(json.get("remaining").isNull).`as`("remaining が null であること").isTrue()
    }

    @Test
    @DisplayName("目標を超過すると残量が負の値になる")
    fun remainingGoesNegativeWhenOverGoal() {
        putGoal(500)
        createMealLog("DINNER", riceFoodId, 500)   // 840 kcal

        mockMvc.perform(auth(get("/api/summaries/daily")).param("date", DATE))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.goal.targetKcal").value(500))
            .andExpect(jsonPath("$.remaining.kcal").value(-340.00))
    }

    @Test
    @DisplayName("期間集計は記録の無い日も 0 kcal で埋めて返す")
    fun rangeSummaryFillsMissingDays() {
        createMealLog("BREAKFAST", riceFoodId, 100)

        mockMvc.perform(
            auth(get("/api/summaries/range")).param("from", "2026-08-15").param("to", DATE)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.days.length()").value(3))
            .andExpect(jsonPath("$.days[0].kcal").value(0))
            .andExpect(jsonPath("$.days[2].date").value(DATE))
            .andExpect(jsonPath("$.days[2].kcal").value(168.00))
    }

    @Test
    @DisplayName("体重の移動平均は記録のある日だけで計算され、空白日は線を途切れさせない")
    fun weightMovingAverageSkipsMissingDays() {
        putWeight("2026-08-15", "70.0")
        // 8/16 は記録なし
        putWeight(DATE, "72.0")

        val result = mockMvc.perform(
            auth(get("/api/summaries/range")).param("from", "2026-08-15").param("to", DATE)
        ).andExpect(status().isOk).andReturn()

        val days = objectMapper.readTree(result.response.contentAsString).get("days")

        assertThat(days.get(0).get("weightMovingAvgKg").asDouble()).isEqualTo(70.00)
        // 記録の無い 8/16 も、直前までの平均を保って線が途切れない
        assertThat(days.get(1).get("weightKg").isNull).isTrue()
        assertThat(days.get(1).get("weightMovingAvgKg").asDouble()).isEqualTo(70.00)
        // (70 + 72) / 2 = 71。空白日を 0 で埋めていたら 47.33 になる
        assertThat(days.get(2).get("weightMovingAvgKg").asDouble()).isEqualTo(71.00)
    }

    @Test
    @DisplayName("期間が逆転していると 400 になる")
    fun invalidRangeRejected() {
        mockMvc.perform(
            auth(get("/api/summaries/range")).param("from", DATE).param("to", "2026-08-15")
        )
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.code").value("INVALID_RANGE"))
    }

    // ------------------------------------------------------- 他ユーザーとの分離

    @Test
    @DisplayName("他人の食事記録は取得できず、削除しようとしても 404 になる")
    fun cannotReachOtherUsersMealLog() {
        val created = createMealLog("BREAKFAST", riceFoodId, 100)
        val mealLogId = objectMapper.readTree(created.response.contentAsString).get("id").asLong()

        val otherToken = signUpAndGetToken()

        // 一覧には現れない
        mockMvc.perform(
            get("/api/meal-logs").header("Authorization", "Bearer $otherToken").param("date", DATE)
        )
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.length()").value(0))

        // ID を直接指定しても、403 ではなく 404
        mockMvc.perform(
            delete("/api/meal-logs/$mealLogId").header("Authorization", "Bearer $otherToken")
        ).andExpect(status().isNotFound)

        // 本来の持ち主からはまだ見える（削除されていない）
        mockMvc.perform(auth(get("/api/meal-logs")).param("date", DATE))
            .andExpect(jsonPath("$.length()").value(1))
    }

    @Test
    @DisplayName("他人が登録した独自食品は検索にも記録にも使えない")
    fun cannotUseOtherUsersFood() {
        val created = mockMvc.perform(
            auth(post("/api/foods")).contentType(MediaType.APPLICATION_JSON).content(
                """{"name":"自家製プロテインバー",
                    "nutritionPer100g":{"kcal":350,"proteinG":30,"fatG":10,"carbG":35}}"""
            )
        ).andExpect(status().isCreated).andReturn()
        val foodId = objectMapper.readTree(created.response.contentAsString).get("id").asLong()

        val otherToken = signUpAndGetToken()

        mockMvc.perform(
            get("/api/foods").header("Authorization", "Bearer $otherToken").param("query", "自家製")
        ).andExpect(jsonPath("$.length()").value(0))

        mockMvc.perform(
            post("/api/meal-logs").header("Authorization", "Bearer $otherToken")
                .contentType(MediaType.APPLICATION_JSON).content(
                    """{"eatenOn":"$DATE","mealType":"SNACK",
                        "items":[{"foodId":$foodId,"amountG":50}]}"""
                )
        )
            .andExpect(status().isNotFound)
            .andExpect(jsonPath("$.code").value("FOOD_NOT_FOUND"))
    }

    // ------------------------------------------------------------------ 食品

    @Test
    @DisplayName("シードデータが検索でき、出所が SEED であることが分かる")
    fun seedFoodsAreSearchable() {
        mockMvc.perform(auth(get("/api/foods")).param("query", "鶏"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$[0].source").value("SEED"))
            .andExpect(jsonPath("$[0].nutritionPer100g.kcal").isNumber)
    }

    @Test
    @DisplayName("自分で登録した食品は共有マスタより先に並ぶ")
    fun ownFoodsRankFirst() {
        mockMvc.perform(
            auth(post("/api/foods")).contentType(MediaType.APPLICATION_JSON).content(
                """{"name":"特製ごはん大盛り",
                    "nutritionPer100g":{"kcal":170,"proteinG":3,"fatG":0.5,"carbG":38}}"""
            )
        ).andExpect(status().isCreated)

        mockMvc.perform(auth(get("/api/foods")).param("query", "ごはん"))
            .andExpect(jsonPath("$[0].source").value("USER"))
            .andExpect(jsonPath("$[0].name").value("特製ごはん大盛り"))
    }

    // ---------------------------------------------------------------- ヘルパー

    private fun auth(builder: MockHttpServletRequestBuilder): MockHttpServletRequestBuilder =
        builder.header("Authorization", "Bearer $token")

    private fun signUpAndGetToken(): String {
        val email = "meal-${UUID.randomUUID()}@example.com"
        val result = mockMvc.perform(
            post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content("""{"email":"$email","password":"password1234","displayName":"テスト"}""")
        ).andExpect(status().isCreated).andReturn()
        return objectMapper.readTree(result.response.contentAsString).get("accessToken").asText()
    }

    private fun findFoodId(name: String): Long {
        val result = mockMvc.perform(auth(get("/api/foods")).param("query", name))
            .andExpect(status().isOk).andReturn()
        val foods: JsonNode = objectMapper.readTree(result.response.contentAsString)
        assertThat(foods).`as`("シードデータに「%s」が存在すること", name).isNotEmpty
        return foods.get(0).get("id").asLong()
    }

    private fun createMealLog(mealType: String, foodId: Long, amountG: Int): MvcResult =
        mockMvc.perform(
            auth(post("/api/meal-logs")).contentType(MediaType.APPLICATION_JSON).content(
                """{"eatenOn":"$DATE","mealType":"$mealType",
                    "items":[{"foodId":$foodId,"amountG":$amountG}]}"""
            )
        ).andExpect(status().isCreated).andReturn()

    private fun putGoal(targetKcal: Int) {
        mockMvc.perform(
            auth(put("/api/goals")).contentType(MediaType.APPLICATION_JSON).content(
                """{"startOn":"2026-08-01","targetKcal":$targetKcal,
                    "targetProteinG":100,"targetFatG":55,"targetCarbG":275}"""
            )
        ).andExpect(status().isOk)
    }

    private fun putWeight(date: String, weightKg: String) {
        mockMvc.perform(
            auth(put("/api/body-records/$date")).contentType(MediaType.APPLICATION_JSON)
                .content("""{"weightKg":$weightKg}""")
        ).andExpect(status().isOk)
    }
}
