package com.example.calorie.summary.dto;

import com.example.calorie.common.Nutrition;
import com.example.calorie.goal.dto.GoalDtos.GoalResponse;
import com.example.calorie.meal.MealType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class SummaryDtos {

    private SummaryDtos() {
    }

    public record MealTypeTotal(MealType mealType, Nutrition total) {
    }

    /**
     * 指定日の集計。ダッシュボードの主データ。
     *
     * @param goal      目標が未設定なら null。集計自体は返せるのでエラーにはしない。
     * @param remaining 目標 − 摂取。超過している場合は負の値になる（0 で切り上げない）。
     *                  超過量が見えないと、どれだけオーバーしたか分からないため。
     * @param weightKg  その日の体重記録。無ければ null。
     */
    public record DailySummaryResponse(
            LocalDate date,
            Nutrition total,
            List<MealTypeTotal> byMealType,
            GoalResponse goal,
            Nutrition remaining,
            BigDecimal weightKg
    ) {
    }

    /**
     * グラフ用の 1 日分。
     *
     * @param targetKcal        その日に有効だった目標。目標は履歴で持つため日ごとに変わりうる。
     * @param weightKg          実測値。記録が無い日は null。
     * @param weightMovingAvgKg 直近 7 日間の記録のある日だけで計算した移動平均。
     */
    public record DailyPoint(
            LocalDate date,
            BigDecimal kcal,
            BigDecimal proteinG,
            BigDecimal fatG,
            BigDecimal carbG,
            Integer targetKcal,
            BigDecimal weightKg,
            BigDecimal weightMovingAvgKg
    ) {
    }

    public record RangeSummaryResponse(
            LocalDate from,
            LocalDate to,
            List<DailyPoint> days
    ) {
    }
}
