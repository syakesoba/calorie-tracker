package com.example.calorie.food.dto

import com.example.calorie.common.Nutrition
import com.example.calorie.food.Food
import com.example.calorie.food.FoodSource
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.math.BigDecimal

/**
 * 栄養値の入力用。
 *
 * ドメインの [Nutrition] と分けているのは、入力時にだけ必要な検証（負値の禁止）を
 * ドメイン側に持ち込まないため。
 */
data class NutritionRequest(
    @field:DecimalMin("0") val kcal: BigDecimal,
    @field:DecimalMin("0") val proteinG: BigDecimal,
    @field:DecimalMin("0") val fatG: BigDecimal,
    @field:DecimalMin("0") val carbG: BigDecimal,
    @field:DecimalMin("0") val saltG: BigDecimal? = null,
    @field:DecimalMin("0") val fiberG: BigDecimal? = null,
    @field:DecimalMin("0") val sugarG: BigDecimal? = null,
) {
    fun toNutrition(): Nutrition = Nutrition(kcal, proteinG, fatG, carbG, saltG, fiberG, sugarG)
}

data class FoodResponse(
    val id: Long,
    val source: FoodSource,
    val name: String,
    val nameKana: String?,
    val category: String?,
    val nutritionPer100g: Nutrition,
) {
    companion object {
        fun from(food: Food) = FoodResponse(
            id = food.id!!,
            source = food.source,
            name = food.name,
            nameKana = food.nameKana,
            category = food.category,
            nutritionPer100g = food.nutritionPer100g(),
        )
    }
}

data class FoodCreateRequest(
    @field:NotBlank @field:Size(max = 200) val name: String,
    @field:Size(max = 200) val nameKana: String? = null,
    @field:Size(max = 100) val category: String? = null,
    @field:Valid val nutritionPer100g: NutritionRequest,
)
