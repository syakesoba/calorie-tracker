package com.example.calorie.common

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * 栄養値。食品マスタでは可食部 100g あたりの値、記録では実際に摂取した量に対する値。
 *
 * 不変の値オブジェクトとして扱い、換算も合算もこのクラスの中だけで行う。
 * 計算がサービス層に散らばると、換算の丸め方が場所ごとに変わってしまうため。
 *
 * [saltG] / [fiberG] / [sugarG] は null を許す。データが無いことと「0 である」ことは
 * 違うため、無いものは無いままにしておく。ただし合算のときだけは、一方が null なら
 * 0 として扱う（[plus] を参照）。
 *
 * ### Java 実装との違い
 * Java では record のコンパクトコンストラクタで引数を再代入し、正規化を
 * 生成コンストラクタの中に組み込めた。Kotlin の data class にはこれに相当する
 * 仕組みが無いため、コンストラクタを private にして `invoke` を入口にしている。
 *
 * さらに `copy()` は private コンストラクタを迂回してしまうため、
 * [ConsistentCopyVisibility] で `copy()` の可視性をコンストラクタに揃えている。
 * 同じ「生成時に正規化する」という要件に対して、Kotlin では手当てが 2 つ増えた。
 */
@ConsistentCopyVisibility
data class Nutrition private constructor(
    val kcal: BigDecimal,
    val proteinG: BigDecimal,
    val fatG: BigDecimal,
    val carbG: BigDecimal,
    val saltG: BigDecimal?,
    val fiberG: BigDecimal?,
    val sugarG: BigDecimal?,
) {

    /**
     * 100g あたりの値から、指定した分量に対する値へ換算する。
     *
     * ```
     * 摂取した栄養値 = 100g あたりの値 × 分量g ÷ 100
     * ```
     */
    fun forAmountGrams(amountG: BigDecimal): Nutrition {
        val ratio = amountG.divide(HUNDRED, 6, RoundingMode.HALF_UP)
        return Nutrition(
            kcal = kcal.scaledTimes(ratio),
            proteinG = proteinG.scaledTimes(ratio),
            fatG = fatG.scaledTimes(ratio),
            carbG = carbG.scaledTimes(ratio),
            saltG = saltG?.scaledTimes(ratio),
            fiberG = fiberG?.scaledTimes(ratio),
            sugarG = sugarG?.scaledTimes(ratio),
        )
    }

    /**
     * 2 つの栄養値を合算する。
     *
     * 任意項目については、**片方が null なら 0 とみなして加算する。**
     * データの無い食品が混ざっていると合計が過小になるが、合計自体を null にすると
     * 「1 品でも塩分不明なら 1 日の塩分が表示されない」ことになり、実用に耐えないため。
     * 両方 null のときだけ null を保つ。
     */
    operator fun plus(other: Nutrition): Nutrition = Nutrition(
        kcal = kcal + other.kcal,
        proteinG = proteinG + other.proteinG,
        fatG = fatG + other.fatG,
        carbG = carbG + other.carbG,
        saltG = addOptional(saltG, other.saltG),
        fiberG = addOptional(fiberG, other.fiberG),
        sugarG = addOptional(sugarG, other.sugarG),
    )

    /**
     * この値から [other] を引く。目標に対する残量の算出に使う。
     * 超過している場合は負の値になる（0 で切り上げない）。
     */
    operator fun minus(other: Nutrition): Nutrition = Nutrition(
        kcal = kcal - other.kcal,
        proteinG = proteinG - other.proteinG,
        fatG = fatG - other.fatG,
        carbG = carbG - other.carbG,
        saltG = subtractOptional(saltG, other.saltG),
        fiberG = subtractOptional(fiberG, other.fiberG),
        sugarG = subtractOptional(sugarG, other.sugarG),
    )

    companion object {
        /**
         * 保持する小数桁数。
         *
         * 表示時ではなく保持時に 2 桁を維持するのは、1 日分を合計したときに
         * 丸め誤差が積み上がるのを防ぐため。整数への丸めは画面表示の段階で行う。
         */
        const val SCALE = 2

        private val HUNDRED = BigDecimal("100")

        private fun zero(): BigDecimal = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP)

        val ZERO: Nutrition = Nutrition(zero(), zero(), zero(), zero(), zero(), zero(), zero())

        /**
         * 生成の唯一の入口。必須項目の null を 0 に寄せ、桁数を揃えて正規化する。
         */
        operator fun invoke(
            kcal: BigDecimal?,
            proteinG: BigDecimal?,
            fatG: BigDecimal?,
            carbG: BigDecimal?,
            saltG: BigDecimal? = null,
            fiberG: BigDecimal? = null,
            sugarG: BigDecimal? = null,
        ): Nutrition = Nutrition(
            kcal = kcal.orZero(),
            proteinG = proteinG.orZero(),
            fatG = fatG.orZero(),
            carbG = carbG.orZero(),
            saltG = saltG?.scaled(),
            fiberG = fiberG?.scaled(),
            sugarG = sugarG?.scaled(),
        )

        private fun BigDecimal?.orZero(): BigDecimal = this?.scaled() ?: zero()

        private fun BigDecimal.scaled(): BigDecimal = setScale(SCALE, RoundingMode.HALF_UP)

        private fun addOptional(a: BigDecimal?, b: BigDecimal?): BigDecimal? =
            if (a == null && b == null) null else (a ?: zero()) + (b ?: zero())

        private fun subtractOptional(a: BigDecimal?, b: BigDecimal?): BigDecimal? =
            if (a == null && b == null) null else (a ?: zero()) - (b ?: zero())
    }
}

/**
 * 掛けた結果を保持桁数に丸める。
 *
 * `operator fun times` として定義すると Kotlin 標準ライブラリの
 * `BigDecimal.times` と競合しうるため、明示的な名前にしてある。
 */
private fun BigDecimal.scaledTimes(ratio: BigDecimal): BigDecimal =
    multiply(ratio).setScale(Nutrition.SCALE, RoundingMode.HALF_UP)
