package com.example.calorie.goal.dto

import com.example.calorie.goal.Goal
import com.example.calorie.goal.GoalCalculator
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 目標の提案。保存されていない計算結果である点が [GoalResponse] と異なる。
 *
 * @param cappedAtBmr 指定ペースでは目標が BMR を下回るため打ち切ったことを示す
 * @param basedOnWeightKg 算出に使った体重（体重記録の最新値）
 */
data class GoalSuggestionResponse(
    val bmr: Int,
    val tdee: Int,
    val targetKcal: Int,
    val targetProteinG: BigDecimal,
    val targetFatG: BigDecimal,
    val targetCarbG: BigDecimal,
    val paceKgPerMonth: BigDecimal,
    val cappedAtBmr: Boolean,
    val basedOnWeightKg: BigDecimal,
) {
    companion object {
        fun from(
            result: GoalCalculator.Result,
            paceKgPerMonth: BigDecimal,
            basedOnWeightKg: BigDecimal,
        ) = GoalSuggestionResponse(
            bmr = result.bmr,
            tdee = result.tdee,
            targetKcal = result.targetKcal,
            targetProteinG = result.targetProteinG,
            targetFatG = result.targetFatG,
            targetCarbG = result.targetCarbG,
            paceKgPerMonth = paceKgPerMonth,
            cappedAtBmr = result.cappedAtBmr,
            basedOnWeightKg = basedOnWeightKg,
        )
    }
}

/**
 * @param startOn 省略した場合は今日。
 */
data class GoalRequest(
    val startOn: LocalDate? = null,
    @field:Min(500) @field:Max(10000) val targetKcal: Int,
    @field:DecimalMin("0") val targetProteinG: BigDecimal,
    @field:DecimalMin("0") val targetFatG: BigDecimal,
    @field:DecimalMin("0") val targetCarbG: BigDecimal,
    @field:DecimalMin("0") val paceKgPerMonth: BigDecimal? = null,
)

data class GoalResponse(
    val id: Long,
    val startOn: LocalDate,
    val targetKcal: Int,
    val targetProteinG: BigDecimal,
    val targetFatG: BigDecimal,
    val targetCarbG: BigDecimal,
    val paceKgPerMonth: BigDecimal?,
) {
    companion object {
        fun from(goal: Goal) = GoalResponse(
            id = goal.id!!,
            startOn = goal.startOn,
            targetKcal = goal.targetKcal,
            targetProteinG = goal.targetProteinG,
            targetFatG = goal.targetFatG,
            targetCarbG = goal.targetCarbG,
            paceKgPerMonth = goal.paceKgPerMonth,
        )
    }
}
