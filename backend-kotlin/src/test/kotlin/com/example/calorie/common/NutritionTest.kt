package com.example.calorie.common

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * Java 実装の NutritionTest と同じケース・同じ期待値を並べている。
 */
class NutritionTest {

    private fun per100g(kcal: String, p: String, f: String, c: String, salt: String?) =
        Nutrition(
            kcal = BigDecimal(kcal),
            proteinG = BigDecimal(p),
            fatG = BigDecimal(f),
            carbG = BigDecimal(c),
            saltG = salt?.let { BigDecimal(it) },
        )

    @Test
    @DisplayName("100g あたりの値から分量に応じて換算される")
    fun convertsByAmount() {
        // ごはん 168kcal/100g を 150g 食べた → 252kcal
        val rice = per100g("168", "2.5", "0.3", "37.1", "0")

        val eaten = rice.forAmountGrams(BigDecimal("150"))

        assertThat(eaten.kcal).isEqualByComparingTo("252.00")
        assertThat(eaten.proteinG).isEqualByComparingTo("3.75")
        assertThat(eaten.carbG).isEqualByComparingTo("55.65")
    }

    @Test
    @DisplayName("小数を含む分量でも小数第2位まで保持する")
    fun keepsTwoDecimals() {
        val food = per100g("105", "23.3", "1.9", "0.1", "0.1")

        val eaten = food.forAmountGrams(BigDecimal("33.3"))

        // 105 × 0.333 = 34.965 → 34.97
        assertThat(eaten.kcal).isEqualByComparingTo("34.97")
    }

    @Test
    @DisplayName("合算できる")
    fun adds() {
        val a = per100g("100", "10", "5", "20", "1")
        val b = per100g("200", "20", "10", "30", "2")

        val total = a + b

        assertThat(total.kcal).isEqualByComparingTo("300")
        assertThat(total.saltG).isEqualByComparingTo("3")
    }

    @Test
    @DisplayName("任意項目は、片方が未設定なら 0 として加算する")
    fun addsOptionalTreatingNullAsZero() {
        val withSalt = per100g("100", "10", "5", "20", "1.5")
        val withoutSalt = per100g("100", "10", "5", "20", null)

        // 塩分不明の食品が 1 品混ざっただけで 1 日の塩分が表示されなくなるのは
        // 実用に耐えないため、未設定は 0 として足す
        assertThat((withSalt + withoutSalt).saltG).isEqualByComparingTo("1.5")
    }

    @Test
    @DisplayName("任意項目が両方とも未設定なら未設定のまま")
    fun keepsNullWhenBothMissing() {
        val a = per100g("100", "10", "5", "20", null)
        val b = per100g("100", "10", "5", "20", null)

        assertThat((a + b).saltG).isNull()
    }

    @Test
    @DisplayName("目標を超過した場合、残量は負の値になる")
    fun remainingGoesNegativeWhenExceeded() {
        val goal = per100g("2000", "100", "55", "275", null)
        val eaten = per100g("2300", "120", "70", "300", null)

        val remaining = goal - eaten

        // 0 で切り上げると、どれだけオーバーしたか分からなくなる
        assertThat(remaining.kcal).isEqualByComparingTo("-300")
    }

    @Test
    @DisplayName("ZERO は加算の単位元として使える")
    fun zeroIsIdentity() {
        val food = per100g("168", "2.5", "0.3", "37.1", "0")

        assertThat((Nutrition.ZERO + food).kcal).isEqualByComparingTo("168")
    }
}
