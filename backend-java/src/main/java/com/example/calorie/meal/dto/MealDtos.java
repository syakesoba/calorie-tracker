package com.example.calorie.meal.dto;

import com.example.calorie.common.Nutrition;
import com.example.calorie.meal.MealLog;
import com.example.calorie.meal.MealLogItem;
import com.example.calorie.meal.MealType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class MealDtos {

    private MealDtos() {
    }

    /**
     * 記録する 1 品。
     *
     * <p>栄養値を受け取らないのは意図的。クライアントが計算して送る形にすると、
     * Web とモバイルで計算式がずれたときに、記録されるデータそのものが食い違う。
     */
    public record MealLogItemRequest(
            @NotNull Long foodId,
            @NotNull @Positive @DecimalMax("10000") BigDecimal amountG
    ) {
    }

    public record MealLogRequest(
            @NotNull LocalDate eatenOn,
            @NotNull MealType mealType,
            @Size(max = 500) String note,
            @NotEmpty @Size(max = 50) @Valid List<MealLogItemRequest> items
    ) {
    }

    public record MealLogItemResponse(
            Long id,
            Long foodId,
            String foodName,
            BigDecimal amountG,
            Nutrition nutrition
    ) {
        public static MealLogItemResponse from(MealLogItem item) {
            return new MealLogItemResponse(
                    item.getId(), item.getFoodId(), item.getFoodName(),
                    item.getAmountG(), item.nutrition());
        }
    }

    public record MealLogResponse(
            Long id,
            LocalDate eatenOn,
            MealType mealType,
            String note,
            List<MealLogItemResponse> items,
            Nutrition total
    ) {
        public static MealLogResponse from(MealLog log) {
            return new MealLogResponse(
                    log.getId(),
                    log.getEatenOn(),
                    log.getMealType(),
                    log.getNote(),
                    log.getItems().stream().map(MealLogItemResponse::from).toList(),
                    log.total()
            );
        }
    }
}
