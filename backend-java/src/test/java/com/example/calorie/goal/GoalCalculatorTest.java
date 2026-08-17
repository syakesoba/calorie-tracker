package com.example.calorie.goal;

import com.example.calorie.profile.ActivityLevel;
import com.example.calorie.profile.Sex;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 目標算出のユニットテスト。DB もフレームワークも使わない。
 *
 * <p><strong>Phase 2 では、このテストケースをそのまま Kotlin 実装に移植して
 * 同じ結果になることを確認する。</strong>期待値をここに固定しておくことが、
 * 2 実装の等価性を担保する土台になる。
 */
class GoalCalculatorTest {

    private static GoalCalculator.Input input(Sex sex, int age, String heightCm,
                                              String weightKg, ActivityLevel level, String pace) {
        return new GoalCalculator.Input(
                sex, age, new BigDecimal(heightCm), new BigDecimal(weightKg),
                level, new BigDecimal(pace));
    }

    @Test
    @DisplayName("男性の BMR は Mifflin-St Jeor 式どおりに算出される")
    void bmrForMale() {
        // 10×70 + 6.25×175 − 5×30 + 5 = 700 + 1093.75 − 150 + 5 = 1648.75 → 1649
        var result = GoalCalculator.calculate(
                input(Sex.MALE, 30, "175", "70", ActivityLevel.SEDENTARY, "0"));

        assertThat(result.bmr()).isEqualTo(1649);
    }

    @Test
    @DisplayName("女性の BMR は男性より 166 kcal 低くなる")
    void bmrForFemale() {
        // 男性 +5 に対して女性は −161 なので、同条件なら差は 166
        var male = GoalCalculator.calculate(
                input(Sex.MALE, 30, "160", "55", ActivityLevel.SEDENTARY, "0"));
        var female = GoalCalculator.calculate(
                input(Sex.FEMALE, 30, "160", "55", ActivityLevel.SEDENTARY, "0"));

        assertThat(male.bmr() - female.bmr()).isEqualTo(166);
    }

    @Test
    @DisplayName("TDEE は活動レベルの係数どおりに増える")
    void tdeeReflectsActivityLevel() {
        var sedentary = GoalCalculator.calculate(
                input(Sex.MALE, 30, "175", "70", ActivityLevel.SEDENTARY, "0"));
        var veryActive = GoalCalculator.calculate(
                input(Sex.MALE, 30, "175", "70", ActivityLevel.VERY_ACTIVE, "0"));

        // BMR 1648.75 × 1.2 = 1978.5 → 1979 / × 1.9 = 3132.625 → 3133
        assertThat(sedentary.tdee()).isEqualTo(1979);
        assertThat(veryActive.tdee()).isEqualTo(3133);
    }

    @Test
    @DisplayName("ペース 0 なら目標は TDEE と一致する（体重維持）")
    void zeroPaceMeansMaintenance() {
        var result = GoalCalculator.calculate(
                input(Sex.MALE, 30, "175", "70", ActivityLevel.MODERATE, "0"));

        assertThat(result.targetKcal()).isEqualTo(result.tdee());
        assertThat(result.cappedAtBmr()).isFalse();
    }

    @Test
    @DisplayName("減量ペースの分だけ目標カロリーが下がる")
    void paceReducesTarget() {
        // 月 2kg → 1日の赤字 = 2 × 7200 ÷ 30 = 480 kcal
        var maintenance = GoalCalculator.calculate(
                input(Sex.MALE, 30, "175", "70", ActivityLevel.MODERATE, "0"));
        var cutting = GoalCalculator.calculate(
                input(Sex.MALE, 30, "175", "70", ActivityLevel.MODERATE, "2"));

        assertThat(maintenance.targetKcal() - cutting.targetKcal()).isEqualTo(480);
    }

    @Test
    @DisplayName("目標が BMR を下回る場合は BMR で打ち切り、その旨をフラグで示す")
    void targetIsCappedAtBmr() {
        // 座位・月4kg減 → 赤字 960 kcal。TDEE 1979 − 960 = 1019 で BMR 1649 を下回る
        var result = GoalCalculator.calculate(
                input(Sex.MALE, 30, "175", "70", ActivityLevel.SEDENTARY, "4"));

        assertThat(result.cappedAtBmr()).isTrue();
        assertThat(result.targetKcal()).isEqualTo(result.bmr());
    }

    @Test
    @DisplayName("目標 PFC は P20% / F25% / C55% で配分され、合計が目標カロリーに一致する")
    void pfcSplitMatchesTargetKcal() {
        var result = GoalCalculator.calculate(
                input(Sex.MALE, 30, "175", "70", ActivityLevel.MODERATE, "0"));

        BigDecimal kcalFromPfc = result.targetProteinG().multiply(BigDecimal.valueOf(4))
                .add(result.targetFatG().multiply(BigDecimal.valueOf(9)))
                .add(result.targetCarbG().multiply(BigDecimal.valueOf(4)));

        // g への丸めがあるため、完全一致ではなく誤差 2 kcal 以内で判定する
        assertThat(kcalFromPfc.doubleValue())
                .isCloseTo(result.targetKcal(), org.assertj.core.data.Offset.offset(2.0));
    }

    @Test
    @DisplayName("年齢が上がると BMR は 1 歳あたり 5 kcal 下がる")
    void bmrDecreasesWithAge() {
        var young = GoalCalculator.calculate(
                input(Sex.FEMALE, 20, "160", "55", ActivityLevel.LIGHT, "0"));
        var older = GoalCalculator.calculate(
                input(Sex.FEMALE, 30, "160", "55", ActivityLevel.LIGHT, "0"));

        assertThat(young.bmr() - older.bmr()).isEqualTo(50);
    }
}
