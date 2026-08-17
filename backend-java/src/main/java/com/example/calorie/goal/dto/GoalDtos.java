package com.example.calorie.goal.dto;

import com.example.calorie.goal.Goal;
import com.example.calorie.goal.GoalCalculator;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class GoalDtos {

    private GoalDtos() {
    }

    /**
     * 目標の提案。保存されていない計算結果である点が {@link GoalResponse} と異なる。
     *
     * @param cappedAtBmr     指定ペースでは目標が BMR を下回るため打ち切ったことを示す
     * @param basedOnWeightKg 算出に使った体重（体重記録の最新値）
     */
    public record GoalSuggestionResponse(
            int bmr,
            int tdee,
            int targetKcal,
            BigDecimal targetProteinG,
            BigDecimal targetFatG,
            BigDecimal targetCarbG,
            BigDecimal paceKgPerMonth,
            boolean cappedAtBmr,
            BigDecimal basedOnWeightKg
    ) {
        public static GoalSuggestionResponse from(GoalCalculator.Result result,
                                                  BigDecimal paceKgPerMonth,
                                                  BigDecimal basedOnWeightKg) {
            return new GoalSuggestionResponse(
                    result.bmr(),
                    result.tdee(),
                    result.targetKcal(),
                    result.targetProteinG(),
                    result.targetFatG(),
                    result.targetCarbG(),
                    paceKgPerMonth,
                    result.cappedAtBmr(),
                    basedOnWeightKg
            );
        }
    }

    /**
     * @param startOn 省略した場合は今日。
     */
    public record GoalRequest(
            LocalDate startOn,
            @NotNull @Min(500) @Max(10000) Integer targetKcal,
            @NotNull @DecimalMin("0") BigDecimal targetProteinG,
            @NotNull @DecimalMin("0") BigDecimal targetFatG,
            @NotNull @DecimalMin("0") BigDecimal targetCarbG,
            @DecimalMin("0") BigDecimal paceKgPerMonth
    ) {
    }

    public record GoalResponse(
            Long id,
            LocalDate startOn,
            int targetKcal,
            BigDecimal targetProteinG,
            BigDecimal targetFatG,
            BigDecimal targetCarbG,
            BigDecimal paceKgPerMonth
    ) {
        public static GoalResponse from(Goal goal) {
            return new GoalResponse(
                    goal.getId(),
                    goal.getStartOn(),
                    goal.getTargetKcal(),
                    goal.getTargetProteinG(),
                    goal.getTargetFatG(),
                    goal.getTargetCarbG(),
                    goal.getPaceKgPerMonth()
            );
        }
    }
}
