package com.example.calorie.goal

import com.example.calorie.profile.ActivityLevel
import com.example.calorie.profile.Sex
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Test
import java.math.BigDecimal

/**
 * 目標算出のユニットテスト。DB もフレームワークも使わない。
 *
 * **Java 実装の GoalCalculatorTest と同じケース・同じ期待値を意図的に並べている。**
 * 両方が同じ数値を返すことが、2 実装の等価性の土台になる。
 * 期待値を変えるときは必ず両方を同時に変えること。
 */
class GoalCalculatorTest {

    private fun input(
        sex: Sex,
        age: Int,
        heightCm: String,
        weightKg: String,
        level: ActivityLevel,
        pace: String,
    ) = GoalCalculator.Input(
        sex = sex,
        age = age,
        heightCm = BigDecimal(heightCm),
        weightKg = BigDecimal(weightKg),
        activityLevel = level,
        paceKgPerMonth = BigDecimal(pace),
    )

    @Test
    @DisplayName("男性の BMR は Mifflin-St Jeor 式どおりに算出される")
    fun bmrForMale() {
        // 10×70 + 6.25×175 − 5×30 + 5 = 700 + 1093.75 − 150 + 5 = 1648.75 → 1649
        val result = GoalCalculator.calculate(
            input(Sex.MALE, 30, "175", "70", ActivityLevel.SEDENTARY, "0")
        )

        assertThat(result.bmr).isEqualTo(1649)
    }

    @Test
    @DisplayName("女性の BMR は男性より 166 kcal 低くなる")
    fun bmrForFemale() {
        // 男性 +5 に対して女性は −161 なので、同条件なら差は 166
        val male = GoalCalculator.calculate(
            input(Sex.MALE, 30, "160", "55", ActivityLevel.SEDENTARY, "0")
        )
        val female = GoalCalculator.calculate(
            input(Sex.FEMALE, 30, "160", "55", ActivityLevel.SEDENTARY, "0")
        )

        assertThat(male.bmr - female.bmr).isEqualTo(166)
    }

    @Test
    @DisplayName("TDEE は活動レベルの係数どおりに増える")
    fun tdeeReflectsActivityLevel() {
        val sedentary = GoalCalculator.calculate(
            input(Sex.MALE, 30, "175", "70", ActivityLevel.SEDENTARY, "0")
        )
        val veryActive = GoalCalculator.calculate(
            input(Sex.MALE, 30, "175", "70", ActivityLevel.VERY_ACTIVE, "0")
        )

        // BMR 1648.75 × 1.2 = 1978.5 → 1979 / × 1.9 = 3132.625 → 3133
        assertThat(sedentary.tdee).isEqualTo(1979)
        assertThat(veryActive.tdee).isEqualTo(3133)
    }

    @Test
    @DisplayName("ペース 0 なら目標は TDEE と一致する（体重維持）")
    fun zeroPaceMeansMaintenance() {
        val result = GoalCalculator.calculate(
            input(Sex.MALE, 30, "175", "70", ActivityLevel.MODERATE, "0")
        )

        assertThat(result.targetKcal).isEqualTo(result.tdee)
        assertThat(result.cappedAtBmr).isFalse()
    }

    @Test
    @DisplayName("減量ペースの分だけ目標カロリーが下がる")
    fun paceReducesTarget() {
        // 月 2kg → 1日の赤字 = 2 × 7200 ÷ 30 = 480 kcal
        val maintenance = GoalCalculator.calculate(
            input(Sex.MALE, 30, "175", "70", ActivityLevel.MODERATE, "0")
        )
        val cutting = GoalCalculator.calculate(
            input(Sex.MALE, 30, "175", "70", ActivityLevel.MODERATE, "2")
        )

        assertThat(maintenance.targetKcal - cutting.targetKcal).isEqualTo(480)
    }

    @Test
    @DisplayName("目標が BMR を下回る場合は BMR で打ち切り、その旨をフラグで示す")
    fun targetIsCappedAtBmr() {
        // 座位・月4kg減 → 赤字 960 kcal。TDEE 1979 − 960 = 1019 で BMR 1649 を下回る
        val result = GoalCalculator.calculate(
            input(Sex.MALE, 30, "175", "70", ActivityLevel.SEDENTARY, "4")
        )

        assertThat(result.cappedAtBmr).isTrue()
        assertThat(result.targetKcal).isEqualTo(result.bmr)
    }

    @Test
    @DisplayName("目標 PFC は P20% / F25% / C55% で配分され、合計が目標カロリーに一致する")
    fun pfcSplitMatchesTargetKcal() {
        val result = GoalCalculator.calculate(
            input(Sex.MALE, 30, "175", "70", ActivityLevel.MODERATE, "0")
        )

        val kcalFromPfc = result.targetProteinG.multiply(BigDecimal.valueOf(4))
            .add(result.targetFatG.multiply(BigDecimal.valueOf(9)))
            .add(result.targetCarbG.multiply(BigDecimal.valueOf(4)))

        // g への丸めがあるため、完全一致ではなく誤差 2 kcal 以内で判定する
        assertThat(kcalFromPfc.toDouble())
            .isCloseTo(result.targetKcal.toDouble(), Offset.offset(2.0))
    }

    @Test
    @DisplayName("年齢が上がると BMR は 1 歳あたり 5 kcal 下がる")
    fun bmrDecreasesWithAge() {
        val young = GoalCalculator.calculate(
            input(Sex.FEMALE, 20, "160", "55", ActivityLevel.LIGHT, "0")
        )
        val older = GoalCalculator.calculate(
            input(Sex.FEMALE, 30, "160", "55", ActivityLevel.LIGHT, "0")
        )

        assertThat(young.bmr - older.bmr).isEqualTo(50)
    }
}
