package com.example.calorie.common;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 栄養値。食品マスタでは可食部 100g あたりの値、記録では実際に摂取した量に対する値。
 *
 * <p>不変の値オブジェクトとして扱い、換算も合算もこのクラスの中だけで行う。
 * 計算がサービス層に散らばると、換算の丸め方が場所ごとに変わってしまうため。
 *
 * <p>{@code saltG} / {@code fiberG} / {@code sugarG} は null を許す。データが無いことと
 * 「0 である」ことは違うため、無いものは無いままにしておく。ただし合算のときだけは、
 * 一方が null なら 0 として扱う（後述）。
 */
public record Nutrition(
        BigDecimal kcal,
        BigDecimal proteinG,
        BigDecimal fatG,
        BigDecimal carbG,
        BigDecimal saltG,
        BigDecimal fiberG,
        BigDecimal sugarG
) {

    /**
     * 保持する小数桁数。
     *
     * <p>表示時ではなく保持時に 2 桁を維持するのは、1 日分を合計したときに
     * 丸め誤差が積み上がるのを防ぐため。整数への丸めは画面表示の段階で行う。
     */
    public static final int SCALE = 2;

    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public static final Nutrition ZERO = new Nutrition(
            zero(), zero(), zero(), zero(), zero(), zero(), zero());

    private static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /** 必須項目の null を 0 に寄せ、桁数を揃えて正規化する。 */
    public Nutrition {
        kcal = normalizeRequired(kcal);
        proteinG = normalizeRequired(proteinG);
        fatG = normalizeRequired(fatG);
        carbG = normalizeRequired(carbG);
        saltG = normalizeOptional(saltG);
        fiberG = normalizeOptional(fiberG);
        sugarG = normalizeOptional(sugarG);
    }

    private static BigDecimal normalizeRequired(BigDecimal value) {
        return value == null ? zero() : value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal normalizeOptional(BigDecimal value) {
        return value == null ? null : value.setScale(SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 100g あたりの値から、指定した分量に対する値へ換算する。
     *
     * <pre>摂取した栄養値 = 100g あたりの値 × 分量g ÷ 100</pre>
     *
     * @param amountG 分量（グラム）
     */
    public Nutrition forAmountGrams(BigDecimal amountG) {
        BigDecimal ratio = amountG.divide(HUNDRED, 6, RoundingMode.HALF_UP);
        return new Nutrition(
                multiply(kcal, ratio),
                multiply(proteinG, ratio),
                multiply(fatG, ratio),
                multiply(carbG, ratio),
                multiply(saltG, ratio),
                multiply(fiberG, ratio),
                multiply(sugarG, ratio)
        );
    }

    /**
     * 2 つの栄養値を合算する。
     *
     * <p>任意項目については、<strong>片方が null なら 0 とみなして加算する。</strong>
     * データの無い食品が混ざっていると合計が過小になるが、合計自体を null にすると
     * 「1 品でも塩分不明なら 1 日の塩分が表示されない」ことになり、実用に耐えないため。
     * 両方 null のときだけ null を保つ。
     */
    public Nutrition plus(Nutrition other) {
        if (other == null) {
            return this;
        }
        return new Nutrition(
                kcal.add(other.kcal),
                proteinG.add(other.proteinG),
                fatG.add(other.fatG),
                carbG.add(other.carbG),
                addOptional(saltG, other.saltG),
                addOptional(fiberG, other.fiberG),
                addOptional(sugarG, other.sugarG)
        );
    }

    /**
     * この値から other を引く。目標に対する残量の算出に使う。
     * 超過している場合は負の値になる（0 で切り上げない）。
     */
    public Nutrition minus(Nutrition other) {
        if (other == null) {
            return this;
        }
        return new Nutrition(
                kcal.subtract(other.kcal),
                proteinG.subtract(other.proteinG),
                fatG.subtract(other.fatG),
                carbG.subtract(other.carbG),
                subtractOptional(saltG, other.saltG),
                subtractOptional(fiberG, other.fiberG),
                subtractOptional(sugarG, other.sugarG)
        );
    }

    private static BigDecimal multiply(BigDecimal value, BigDecimal ratio) {
        return value == null ? null : value.multiply(ratio).setScale(SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal addOptional(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) {
            return null;
        }
        return (a == null ? zero() : a).add(b == null ? zero() : b);
    }

    private static BigDecimal subtractOptional(BigDecimal a, BigDecimal b) {
        if (a == null && b == null) {
            return null;
        }
        return (a == null ? zero() : a).subtract(b == null ? zero() : b);
    }
}
