package com.example.calorie.food.dto;

import com.example.calorie.common.Nutrition;
import com.example.calorie.food.Food;
import com.example.calorie.food.FoodSource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/** 食品まわりの入出力 DTO。{@code api/openapi.yaml} の foods 系スキーマと対応する。 */
public final class FoodDtos {

    private FoodDtos() {
    }

    /**
     * 栄養値の入力用。
     *
     * <p>ドメインの {@link Nutrition} と分けているのは、入力時にだけ必要な検証
     * （負値の禁止）をドメイン側に持ち込まないため。
     */
    public record NutritionRequest(
            @NotNull @DecimalMin("0") BigDecimal kcal,
            @NotNull @DecimalMin("0") BigDecimal proteinG,
            @NotNull @DecimalMin("0") BigDecimal fatG,
            @NotNull @DecimalMin("0") BigDecimal carbG,
            @DecimalMin("0") BigDecimal saltG,
            @DecimalMin("0") BigDecimal fiberG,
            @DecimalMin("0") BigDecimal sugarG
    ) {
        public Nutrition toNutrition() {
            return new Nutrition(kcal, proteinG, fatG, carbG, saltG, fiberG, sugarG);
        }
    }

    public record FoodResponse(
            Long id,
            FoodSource source,
            String name,
            String nameKana,
            String category,
            Nutrition nutritionPer100g
    ) {
        public static FoodResponse from(Food food) {
            return new FoodResponse(
                    food.getId(),
                    food.getSource(),
                    food.getName(),
                    food.getNameKana(),
                    food.getCategory(),
                    food.nutritionPer100g()
            );
        }
    }

    public record FoodCreateRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 200) String nameKana,
            @Size(max = 100) String category,
            @NotNull @Valid NutritionRequest nutritionPer100g
    ) {
    }
}
