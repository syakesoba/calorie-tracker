package com.example.calorie.summary;

import com.example.calorie.body.BodyRecord;
import com.example.calorie.body.BodyRecordRepository;
import com.example.calorie.common.DateRanges;
import com.example.calorie.common.Nutrition;
import com.example.calorie.goal.Goal;
import com.example.calorie.goal.GoalRepository;
import com.example.calorie.goal.dto.GoalDtos.GoalResponse;
import com.example.calorie.meal.MealLog;
import com.example.calorie.meal.MealLogRepository;
import com.example.calorie.meal.MealType;
import com.example.calorie.summary.dto.SummaryDtos.DailyPoint;
import com.example.calorie.summary.dto.SummaryDtos.DailySummaryResponse;
import com.example.calorie.summary.dto.SummaryDtos.MealTypeTotal;
import com.example.calorie.summary.dto.SummaryDtos.RangeSummaryResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 栄養集計。
 *
 * <p>集計は SQL の {@code sum()} ではなくアプリケーション側で行っている。
 * 栄養値の合算規則（任意項目の null 扱い）を {@link Nutrition} 1 箇所に閉じ込めるため。
 * SQL に散らすと、同じ規則を複数の集計クエリに書き写すことになる。
 */
@Service
public class SummaryService {

    /** 体重移動平均の窓幅（日）。 */
    private static final int WEIGHT_MOVING_AVERAGE_DAYS = 7;

    private final MealLogRepository mealLogRepository;
    private final BodyRecordRepository bodyRecordRepository;
    private final GoalRepository goalRepository;

    public SummaryService(MealLogRepository mealLogRepository,
                          BodyRecordRepository bodyRecordRepository,
                          GoalRepository goalRepository) {
        this.mealLogRepository = mealLogRepository;
        this.bodyRecordRepository = bodyRecordRepository;
        this.goalRepository = goalRepository;
    }

    @Transactional(readOnly = true)
    public DailySummaryResponse daily(Long userId, LocalDate date) {
        List<MealLog> logs = mealLogRepository.findByUserIdAndDate(userId, date);

        Nutrition total = logs.stream()
                .map(MealLog::total)
                .reduce(Nutrition.ZERO, Nutrition::plus);

        // 食事区分ごとの内訳。列挙の宣言順（朝食→昼食→夕食→間食）を保ちたいので EnumMap。
        Map<MealType, Nutrition> byType = new EnumMap<>(MealType.class);
        for (MealLog log : logs) {
            byType.merge(log.getMealType(), log.total(), Nutrition::plus);
        }
        List<MealTypeTotal> breakdown = byType.entrySet().stream()
                .map(e -> new MealTypeTotal(e.getKey(), e.getValue()))
                .toList();

        Goal goal = goalRepository
                .findFirstByUserIdAndStartOnLessThanEqualOrderByStartOnDesc(userId, date)
                .orElse(null);

        // 目標が未設定でも集計自体は返せるので、エラーにせず null を返す
        Nutrition remaining = (goal == null) ? null : goalAsNutrition(goal).minus(total);

        BigDecimal weight = bodyRecordRepository.findByUserIdAndRecordedOn(userId, date)
                .map(BodyRecord::getWeightKg)
                .orElse(null);

        return new DailySummaryResponse(
                date,
                total,
                breakdown,
                goal == null ? null : GoalResponse.from(goal),
                remaining,
                weight
        );
    }

    @Transactional(readOnly = true)
    public RangeSummaryResponse range(Long userId, LocalDate from, LocalDate to) {
        DateRanges.requireValid(from, to);

        // 期間分の食事・体重・目標をそれぞれ 1 回ずつ読む。
        // 日ごとに問い合わせると 30 日のグラフで 90 回のクエリになる。
        Map<LocalDate, Nutrition> kcalByDate = mealLogRepository
                .findByUserIdAndDateRange(userId, from, to).stream()
                .collect(Collectors.toMap(
                        MealLog::getEatenOn, MealLog::total, Nutrition::plus));

        Map<LocalDate, BigDecimal> weightByDate = bodyRecordRepository
                .findByUserIdAndRecordedOnBetweenOrderByRecordedOnAsc(userId, from, to).stream()
                .collect(Collectors.toMap(BodyRecord::getRecordedOn, BodyRecord::getWeightKg));

        // 期間開始より前に設定された目標も必要なので、上限だけで絞って降順に取る
        List<Goal> goals = goalRepository.findByUserIdAndStartOnLessThanEqualOrderByStartOnDesc(userId, to);

        List<DailyPoint> days = new ArrayList<>();
        MovingAverage movingAverage = new MovingAverage(WEIGHT_MOVING_AVERAGE_DAYS);

        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            Nutrition nutrition = kcalByDate.getOrDefault(date, Nutrition.ZERO);
            BigDecimal weight = weightByDate.get(date);

            // 記録が無い日は移動平均の計算から除外する。0 として扱うと線が急落してしまう。
            if (weight != null) {
                movingAverage.add(weight);
            }

            days.add(new DailyPoint(
                    date,
                    nutrition.kcal(),
                    nutrition.proteinG(),
                    nutrition.fatG(),
                    nutrition.carbG(),
                    effectiveGoalOn(goals, date).map(Goal::getTargetKcal).orElse(null),
                    weight,
                    movingAverage.currentValue()
            ));
        }

        return new RangeSummaryResponse(from, to, days);
    }

    /** その日に有効だった目標。降順リストの先頭から、start_on が当日以前の最初のもの。 */
    private static java.util.Optional<Goal> effectiveGoalOn(List<Goal> goalsDesc, LocalDate date) {
        return goalsDesc.stream()
                .filter(g -> !g.getStartOn().isAfter(date))
                .findFirst();
    }

    /** 目標値を Nutrition として扱い、残量計算を Nutrition 側の規則に任せる。 */
    private static Nutrition goalAsNutrition(Goal goal) {
        return new Nutrition(
                BigDecimal.valueOf(goal.getTargetKcal()),
                goal.getTargetProteinG(),
                goal.getTargetFatG(),
                goal.getTargetCarbG(),
                null, null, null
        );
    }

    /**
     * 直近 N 件の単純移動平均。
     *
     * <p>「直近 N 日」ではなく「記録のある直近 N 件」で計算する。記録が飛び飛びの
     * 場合に、空白日を 0 で埋めると平均が実態から外れるため。
     */
    private static final class MovingAverage {

        private final int windowSize;
        private final Deque<BigDecimal> window = new ArrayDeque<>();
        private BigDecimal sum = BigDecimal.ZERO;

        MovingAverage(int windowSize) {
            this.windowSize = windowSize;
        }

        void add(BigDecimal value) {
            window.addLast(value);
            sum = sum.add(value);
            if (window.size() > windowSize) {
                sum = sum.subtract(window.removeFirst());
            }
        }

        /** まだ 1 件も記録が無ければ null。 */
        BigDecimal currentValue() {
            if (window.isEmpty()) {
                return null;
            }
            return sum.divide(BigDecimal.valueOf(window.size()), 2, RoundingMode.HALF_UP);
        }
    }
}
