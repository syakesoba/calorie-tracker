package com.example.calorie.goal

import com.example.calorie.profile.ActivityLevel
import com.example.calorie.profile.Sex
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 目標カロリーと目標 PFC の算出。
 *
 * **このオブジェクトは DB もリクエストも触らない純粋関数である。**
 * そうしている理由が 2 つある。ユニットテストが値の入出力だけで書けること、
 * そして Java 実装と同じテストケースをそのまま流して等価性を確認できること。
 *
 * 採用式は Mifflin-St Jeor。これは推定式であり個人差がある。
 * 健康上の判断に使うものではない。
 *
 * Java 側は `private` コンストラクタを持つ final クラスでインスタンス化を防いでいるが、
 * Kotlin では `object` 宣言でそれが言語機能として得られる。
 */
object GoalCalculator {

    /** 体脂肪 1kg の消費に必要なおおよそのエネルギー（kcal）。 */
    private val KCAL_PER_KG_FAT = BigDecimal("7200")

    /** 月間ペースを 1 日あたりに割るときの日数。月ごとの日数差は誤差として無視する。 */
    private val DAYS_PER_MONTH = BigDecimal("30")

    /** 目標 PFC の既定比率。合計は 1.00 になること。 */
    private val PROTEIN_RATIO = BigDecimal("0.20")
    private val FAT_RATIO = BigDecimal("0.25")
    private val CARB_RATIO = BigDecimal("0.55")

    /** 1g あたりのエネルギー（Atwater 係数）。 */
    private val KCAL_PER_G_PROTEIN = BigDecimal("4")
    private val KCAL_PER_G_FAT = BigDecimal("9")
    private val KCAL_PER_G_CARB = BigDecimal("4")

    /**
     * 算出の入力。
     *
     * @param weightKg 体重。プロフィールではなく**体重記録の最新値**を渡すこと。
     *   体重は日々変わるものであり、プロフィールに固定値として持たせると実態とずれる。
     * @param paceKgPerMonth 1 か月あたりの目標減量キログラム数。0 なら体重維持。
     */
    data class Input(
        val sex: Sex,
        val age: Int,
        val heightCm: BigDecimal,
        val weightKg: BigDecimal,
        val activityLevel: ActivityLevel,
        val paceKgPerMonth: BigDecimal,
    )

    /**
     * 算出の結果。
     *
     * @param cappedAtBmr 指定したペースでは目標が BMR を下回るため、BMR で打ち切った
     *   ことを示す。画面で「このペースは達成できない」と伝えるために使う。
     */
    data class Result(
        val bmr: Int,
        val tdee: Int,
        val targetKcal: Int,
        val targetProteinG: BigDecimal,
        val targetFatG: BigDecimal,
        val targetCarbG: BigDecimal,
        val cappedAtBmr: Boolean,
    )

    fun calculate(input: Input): Result {
        val bmr = basalMetabolicRate(input)
        val tdee = bmr.multiply(input.activityLevel.factor)

        // 1 日あたりの目標赤字 = 月間目標減量kg × 7200 ÷ 30
        val dailyDeficit = input.paceKgPerMonth
            .multiply(KCAL_PER_KG_FAT)
            .divide(DAYS_PER_MONTH, 4, RoundingMode.HALF_UP)

        val rawTarget = tdee.subtract(dailyDeficit)

        // 減量ペースを上げるほど目標が際限なく下がってしまうため、BMR を下限とする。
        // 基礎代謝を下回る摂取を目標として提示すべきではない。
        val cappedAtBmr = rawTarget < bmr
        val target = if (cappedAtBmr) bmr else rawTarget

        val targetKcal = target.setScale(0, RoundingMode.HALF_UP).toInt()

        return Result(
            bmr = bmr.setScale(0, RoundingMode.HALF_UP).toInt(),
            tdee = tdee.setScale(0, RoundingMode.HALF_UP).toInt(),
            targetKcal = targetKcal,
            targetProteinG = gramsFor(targetKcal, PROTEIN_RATIO, KCAL_PER_G_PROTEIN),
            targetFatG = gramsFor(targetKcal, FAT_RATIO, KCAL_PER_G_FAT),
            targetCarbG = gramsFor(targetKcal, CARB_RATIO, KCAL_PER_G_CARB),
            cappedAtBmr = cappedAtBmr,
        )
    }

    /**
     * Mifflin-St Jeor 式による基礎代謝量。
     *
     * ```
     * 男性: 10 × 体重kg + 6.25 × 身長cm − 5 × 年齢 + 5
     * 女性: 10 × 体重kg + 6.25 × 身長cm − 5 × 年齢 − 161
     * ```
     *
     * Java の switch 式に対して、Kotlin では `when` 式が使える。
     * enum を網羅していれば else が不要なのは両言語で同じ。
     */
    private fun basalMetabolicRate(input: Input): BigDecimal {
        val base = BigDecimal("10").multiply(input.weightKg)
            .add(BigDecimal("6.25").multiply(input.heightCm))
            .subtract(BigDecimal("5").multiply(BigDecimal.valueOf(input.age.toLong())))

        return when (input.sex) {
            Sex.MALE -> base.add(BigDecimal("5"))
            Sex.FEMALE -> base.subtract(BigDecimal("161"))
        }
    }

    /** 目標カロリーのうち指定比率を、その栄養素のグラム数に換算する。 */
    private fun gramsFor(targetKcal: Int, ratio: BigDecimal, kcalPerGram: BigDecimal): BigDecimal =
        BigDecimal.valueOf(targetKcal.toLong())
            .multiply(ratio)
            .divide(kcalPerGram, 1, RoundingMode.HALF_UP)
}
