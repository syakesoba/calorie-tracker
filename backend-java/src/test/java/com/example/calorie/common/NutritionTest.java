package com.example.calorie.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class NutritionTest {

    private static Nutrition per100g(String kcal, String p, String f, String c, String salt) {
        return new Nutrition(
                new BigDecimal(kcal), new BigDecimal(p), new BigDecimal(f), new BigDecimal(c),
                salt == null ? null : new BigDecimal(salt), null, null);
    }

    @Test
    @DisplayName("100g あたりの値から分量に応じて換算される")
    void convertsByAmount() {
        // ごはん 168kcal/100g を 150g 食べた → 252kcal
        Nutrition rice = per100g("168", "2.5", "0.3", "37.1", "0");

        Nutrition eaten = rice.forAmountGrams(new BigDecimal("150"));

        assertThat(eaten.kcal()).isEqualByComparingTo("252.00");
        assertThat(eaten.proteinG()).isEqualByComparingTo("3.75");
        assertThat(eaten.carbG()).isEqualByComparingTo("55.65");
    }

    @Test
    @DisplayName("小数を含む分量でも小数第2位まで保持する")
    void keepsTwoDecimals() {
        Nutrition food = per100g("105", "23.3", "1.9", "0.1", "0.1");

        Nutrition eaten = food.forAmountGrams(new BigDecimal("33.3"));

        // 105 × 0.333 = 34.965 → 34.97
        assertThat(eaten.kcal()).isEqualByComparingTo("34.97");
    }

    @Test
    @DisplayName("合算できる")
    void adds() {
        Nutrition a = per100g("100", "10", "5", "20", "1");
        Nutrition b = per100g("200", "20", "10", "30", "2");

        Nutrition total = a.plus(b);

        assertThat(total.kcal()).isEqualByComparingTo("300");
        assertThat(total.saltG()).isEqualByComparingTo("3");
    }

    @Test
    @DisplayName("任意項目は、片方が未設定なら 0 として加算する")
    void addsOptionalTreatingNullAsZero() {
        Nutrition withSalt = per100g("100", "10", "5", "20", "1.5");
        Nutrition withoutSalt = per100g("100", "10", "5", "20", null);

        // 塩分不明の食品が 1 品混ざっただけで 1 日の塩分が表示されなくなるのは
        // 実用に耐えないため、未設定は 0 として足す
        assertThat(withSalt.plus(withoutSalt).saltG()).isEqualByComparingTo("1.5");
    }

    @Test
    @DisplayName("任意項目が両方とも未設定なら未設定のまま")
    void keepsNullWhenBothMissing() {
        Nutrition a = per100g("100", "10", "5", "20", null);
        Nutrition b = per100g("100", "10", "5", "20", null);

        assertThat(a.plus(b).saltG()).isNull();
    }

    @Test
    @DisplayName("目標を超過した場合、残量は負の値になる")
    void remainingGoesNegativeWhenExceeded() {
        Nutrition goal = per100g("2000", "100", "55", "275", null);
        Nutrition eaten = per100g("2300", "120", "70", "300", null);

        Nutrition remaining = goal.minus(eaten);

        // 0 で切り上げると、どれだけオーバーしたか分からなくなる
        assertThat(remaining.kcal()).isEqualByComparingTo("-300");
    }

    @Test
    @DisplayName("ZERO は加算の単位元として使える")
    void zeroIsIdentity() {
        Nutrition food = per100g("168", "2.5", "0.3", "37.1", "0");

        assertThat(Nutrition.ZERO.plus(food).kcal()).isEqualByComparingTo("168");
    }
}
