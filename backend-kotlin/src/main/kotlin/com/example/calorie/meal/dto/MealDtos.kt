package com.example.calorie.meal.dto

import com.example.calorie.common.Nutrition
import com.example.calorie.meal.MealLog
import com.example.calorie.meal.MealLogItem
import com.example.calorie.meal.MealType
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.NotEmpty
import jakarta.validation.constraints.Positive
import jakarta.validation.constraints.Size
import java.math.BigDecimal
import java.time.LocalDate

/**
 * 記録する 1 品。
 *
 * 栄養値を受け取らないのは意図的。クライアントが計算して送る形にすると、
 * Web とモバイルで計算式がずれたときに、記録されるデータそのものが食い違う。
 */
data class MealLogItemRequest(
    val foodId: Long,
    @field:Positive @field:DecimalMax("10000") val amountG: BigDecimal,
)

data class MealLogRequest(
    val eatenOn: LocalDate,
    val mealType: MealType,
    @field:Size(max = 500) val note: String? = null,
    @field:NotEmpty @field:Size(max = 50) @field:Valid val items: List<MealLogItemRequest>,
)

data class MealLogItemResponse(
    val id: Long,
    val foodId: Long?,
    val foodName: String,
    val amountG: BigDecimal,
    val nutrition: Nutrition,
) {
    companion object {
        fun from(item: MealLogItem) = MealLogItemResponse(
            id = item.id!!,
            foodId = item.foodId,
            foodName = item.foodName,
            amountG = item.amountG,
            nutrition = item.nutrition(),
        )
    }
}

data class MealLogResponse(
    val id: Long,
    val eatenOn: LocalDate,
    val mealType: MealType,
    val note: String?,
    val items: List<MealLogItemResponse>,
    val total: Nutrition,
) {
    companion object {
        fun from(log: MealLog) = MealLogResponse(
            id = log.id!!,
            eatenOn = log.eatenOn,
            mealType = log.mealType,
            note = log.note,
            items = log.items.map(MealLogItemResponse::from),
            total = log.total(),
        )
    }
}
