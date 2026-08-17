package com.example.calorie.goal;

import com.example.calorie.profile.ActivityLevel;
import com.example.calorie.profile.Sex;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 目標カロリーと目標 PFC の算出。
 *
 * <p><strong>このクラスは DB もリクエストも触らない純粋関数である。</strong>
 * そうしている理由が 2 つある。ユニットテストが値の入出力だけで書けること、
 * そして Phase 2 で Kotlin に移植したときに、同じテストケースをそのまま流して
 * 等価性を確認できること。言語比較の題材としても、副作用のない計算は
 * 差分が読み取りやすい。
 *
 * <p>採用式は Mifflin-St Jeor。これは推定式であり個人差がある。
 * 健康上の判断に使うものではない。
 */
public final class GoalCalculator {

    /** 体脂肪 1kg の消費に必要なおおよそのエネルギー（kcal）。 */
    private static final BigDecimal KCAL_PER_KG_FAT = new BigDecimal("7200");

    /** 月間ペースを 1 日あたりに割るときの日数。月ごとの日数差は誤差として無視する。 */
    private static final BigDecimal DAYS_PER_MONTH = new BigDecimal("30");

    /** 目標 PFC の既定比率。合計は 1.00 になること。 */
    private static final BigDecimal PROTEIN_RATIO = new BigDecimal("0.20");
    private static final BigDecimal FAT_RATIO = new BigDecimal("0.25");
    private static final BigDecimal CARB_RATIO = new BigDecimal("0.55");

    /** 1g あたりのエネルギー（Atwater 係数）。 */
    private static final BigDecimal KCAL_PER_G_PROTEIN = new BigDecimal("4");
    private static final BigDecimal KCAL_PER_G_FAT = new BigDecimal("9");
    private static final BigDecimal KCAL_PER_G_CARB = new BigDecimal("4");

    private GoalCalculator() {
    }

    /**
     * 算出の入力。
     *
     * @param weightKg 体重。プロフィールではなく<strong>体重記録の最新値</strong>を渡すこと。
     *                 体重は日々変わるものであり、プロフィールに固定値として持たせると
     *                 実態とずれるため。
     * @param paceKgPerMonth 1 か月あたりの目標減量キログラム数。0 なら体重維持。
     */
    public record Input(
            Sex sex,
            int age,
            BigDecimal heightCm,
            BigDecimal weightKg,
            ActivityLevel activityLevel,
            BigDecimal paceKgPerMonth
    ) {
    }

    /**
     * 算出の結果。
     *
     * @param cappedAtBmr 指定したペースでは目標が BMR を下回るため、BMR で打ち切ったことを示す。
     *                    画面で「このペースは達成できない」と伝えるために使う。
     */
    public record Result(
            int bmr,
            int tdee,
            int targetKcal,
            BigDecimal targetProteinG,
            BigDecimal targetFatG,
            BigDecimal targetCarbG,
            boolean cappedAtBmr
    ) {
    }

    public static Result calculate(Input input) {
        BigDecimal bmr = basalMetabolicRate(input);
        BigDecimal tdee = bmr.multiply(input.activityLevel().factor());

        // 1 日あたりの目標赤字 = 月間目標減量kg × 7200 ÷ 30
        BigDecimal dailyDeficit = input.paceKgPerMonth()
                .multiply(KCAL_PER_KG_FAT)
                .divide(DAYS_PER_MONTH, 4, RoundingMode.HALF_UP);

        BigDecimal rawTarget = tdee.subtract(dailyDeficit);

        // 減量ペースを上げるほど目標が際限なく下がってしまうため、BMR を下限とする。
        // 基礎代謝を下回る摂取を目標として提示すべきではない。
        boolean cappedAtBmr = rawTarget.compareTo(bmr) < 0;
        BigDecimal target = cappedAtBmr ? bmr : rawTarget;

        int targetKcal = target.setScale(0, RoundingMode.HALF_UP).intValue();

        return new Result(
                bmr.setScale(0, RoundingMode.HALF_UP).intValue(),
                tdee.setScale(0, RoundingMode.HALF_UP).intValue(),
                targetKcal,
                gramsFor(targetKcal, PROTEIN_RATIO, KCAL_PER_G_PROTEIN),
                gramsFor(targetKcal, FAT_RATIO, KCAL_PER_G_FAT),
                gramsFor(targetKcal, CARB_RATIO, KCAL_PER_G_CARB),
                cappedAtBmr
        );
    }

    /**
     * Mifflin-St Jeor 式による基礎代謝量。
     *
     * <pre>
     * 男性: 10 × 体重kg + 6.25 × 身長cm − 5 × 年齢 + 5
     * 女性: 10 × 体重kg + 6.25 × 身長cm − 5 × 年齢 − 161
     * </pre>
     */
    private static BigDecimal basalMetabolicRate(Input input) {
        BigDecimal base = new BigDecimal("10").multiply(input.weightKg())
                .add(new BigDecimal("6.25").multiply(input.heightCm()))
                .subtract(new BigDecimal("5").multiply(BigDecimal.valueOf(input.age())));

        return switch (input.sex()) {
            case MALE -> base.add(new BigDecimal("5"));
            case FEMALE -> base.subtract(new BigDecimal("161"));
        };
    }

    /** 目標カロリーのうち指定比率を、その栄養素のグラム数に換算する。 */
    private static BigDecimal gramsFor(int targetKcal, BigDecimal ratio, BigDecimal kcalPerGram) {
        return BigDecimal.valueOf(targetKcal)
                .multiply(ratio)
                .divide(kcalPerGram, 1, RoundingMode.HALF_UP);
    }
}
