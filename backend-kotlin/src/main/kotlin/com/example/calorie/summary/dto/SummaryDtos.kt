package com.example.calorie.summary.dto

import com.example.calorie.common.Nutrition
import com.example.calorie.goal.dto.GoalResponse
import com.example.calorie.meal.MealType
import java.math.BigDecimal
import java.time.LocalDate

data class MealTypeTotal(val mealType: MealType, val total: Nutrition)

/**
 * 指定日の集計。ダッシュボードの主データ。
 *
 * @param goal 目標が未設定なら null。集計自体は返せるのでエラーにはしない。
 * @param remaining 目標 − 摂取。超過している場合は負の値になる（0 で切り上げない）。
 *   超過量が見えないと、どれだけオーバーしたか分からないため。
 * @param weightKg その日の体重記録。無ければ null。
 */
data class DailySummaryResponse(
    val date: LocalDate,
    val total: Nutrition,
    val byMealType: List<MealTypeTotal>,
    val goal: GoalResponse?,
    val remaining: Nutrition?,
    val weightKg: BigDecimal?,
)

/**
 * グラフ用の 1 日分。
 *
 * @param targetKcal その日に有効だった目標。目標は履歴で持つため日ごとに変わりうる。
 * @param weightKg 実測値。記録が無い日は null。
 * @param weightMovingAvgKg 直近 7 日間の記録のある日だけで計算した移動平均。
 */
data class DailyPoint(
    val date: LocalDate,
    val kcal: BigDecimal,
    val proteinG: BigDecimal,
    val fatG: BigDecimal,
    val carbG: BigDecimal,
    val targetKcal: Int?,
    val weightKg: BigDecimal?,
    val weightMovingAvgKg: BigDecimal?,
)

data class RangeSummaryResponse(
    val from: LocalDate,
    val to: LocalDate,
    val days: List<DailyPoint>,
)
