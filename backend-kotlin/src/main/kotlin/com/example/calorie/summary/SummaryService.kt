package com.example.calorie.summary

import com.example.calorie.body.BodyRecordRepository
import com.example.calorie.common.DateRanges
import com.example.calorie.common.Nutrition
import com.example.calorie.goal.Goal
import com.example.calorie.goal.GoalRepository
import com.example.calorie.goal.dto.GoalResponse
import com.example.calorie.meal.MealLog
import com.example.calorie.meal.MealLogRepository
import com.example.calorie.meal.MealType
import com.example.calorie.summary.dto.DailyPoint
import com.example.calorie.summary.dto.DailySummaryResponse
import com.example.calorie.summary.dto.MealTypeTotal
import com.example.calorie.summary.dto.RangeSummaryResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.util.ArrayDeque
import java.util.EnumMap

/**
 * 栄養集計。
 *
 * 集計は SQL の `sum()` ではなくアプリケーション側で行っている。
 * 栄養値の合算規則（任意項目の null 扱い）を [Nutrition] 1 箇所に閉じ込めるため。
 * SQL に散らすと、同じ規則を複数の集計クエリに書き写すことになる。
 */
@Service
class SummaryService(
    private val mealLogRepository: MealLogRepository,
    private val bodyRecordRepository: BodyRecordRepository,
    private val goalRepository: GoalRepository,
) {

    @Transactional(readOnly = true)
    fun daily(userId: Long, date: LocalDate): DailySummaryResponse {
        val logs = mealLogRepository.findByUserIdAndDate(userId, date)

        val total = logs.fold(Nutrition.ZERO) { acc, log -> acc + log.total() }

        // 食事区分ごとの内訳。列挙の宣言順（朝食→昼食→夕食→間食）を保ちたいので EnumMap。
        val byType = EnumMap<MealType, Nutrition>(MealType::class.java)
        for (log in logs) {
            byType.merge(log.mealType, log.total()) { a, b -> a + b }
        }
        val breakdown = byType.map { (type, nutrition) -> MealTypeTotal(type, nutrition) }

        val goal = goalRepository
            .findFirstByUserIdAndStartOnLessThanEqualOrderByStartOnDesc(userId, date)

        // 目標が未設定でも集計自体は返せるので、エラーにせず null を返す
        val remaining = goal?.let { it.asNutrition() - total }

        val weight = bodyRecordRepository.findByUserIdAndRecordedOn(userId, date)?.weightKg

        return DailySummaryResponse(
            date = date,
            total = total,
            byMealType = breakdown,
            goal = goal?.let(GoalResponse::from),
            remaining = remaining,
            weightKg = weight,
        )
    }

    @Transactional(readOnly = true)
    fun range(userId: Long, from: LocalDate, to: LocalDate): RangeSummaryResponse {
        DateRanges.requireValid(from, to)

        // 期間分の食事・体重・目標をそれぞれ 1 回ずつ読む。
        // 日ごとに問い合わせると 30 日のグラフで 90 回のクエリになる。
        val nutritionByDate = mealLogRepository.findByUserIdAndDateRange(userId, from, to)
            .groupingBy(MealLog::eatenOn)
            .fold(Nutrition.ZERO) { acc, log -> acc + log.total() }

        val weightByDate = bodyRecordRepository
            .findByUserIdAndRecordedOnBetweenOrderByRecordedOnAsc(userId, from, to)
            .associate { it.recordedOn to it.weightKg }

        // 期間開始より前に設定された目標も必要なので、上限だけで絞って降順に取る
        val goalsDesc = goalRepository.findByUserIdAndStartOnLessThanEqualOrderByStartOnDesc(userId, to)

        val movingAverage = MovingAverage(WEIGHT_MOVING_AVERAGE_DAYS)
        val days = mutableListOf<DailyPoint>()

        var date = from
        while (!date.isAfter(to)) {
            val nutrition = nutritionByDate[date] ?: Nutrition.ZERO
            val weight = weightByDate[date]

            // 記録が無い日は移動平均の計算から除外する。0 として扱うと線が急落してしまう。
            if (weight != null) {
                movingAverage.add(weight)
            }

            days.add(
                DailyPoint(
                    date = date,
                    kcal = nutrition.kcal,
                    proteinG = nutrition.proteinG,
                    fatG = nutrition.fatG,
                    carbG = nutrition.carbG,
                    targetKcal = goalsDesc.effectiveOn(date)?.targetKcal,
                    weightKg = weight,
                    weightMovingAvgKg = movingAverage.currentValue(),
                )
            )
            date = date.plusDays(1)
        }

        return RangeSummaryResponse(from, to, days)
    }

    /** その日に有効だった目標。降順リストの先頭から、start_on が当日以前の最初のもの。 */
    private fun List<Goal>.effectiveOn(date: LocalDate): Goal? =
        firstOrNull { !it.startOn.isAfter(date) }

    /** 目標値を Nutrition として扱い、残量計算を Nutrition 側の規則に任せる。 */
    private fun Goal.asNutrition(): Nutrition = Nutrition(
        kcal = BigDecimal.valueOf(targetKcal.toLong()),
        proteinG = targetProteinG,
        fatG = targetFatG,
        carbG = targetCarbG,
    )

    /**
     * 直近 N 件の単純移動平均。
     *
     * 「直近 N 日」ではなく「記録のある直近 N 件」で計算する。記録が飛び飛びの場合に、
     * 空白日を 0 で埋めると平均が実態から外れるため。
     */
    private class MovingAverage(private val windowSize: Int) {
        private val window = ArrayDeque<BigDecimal>()
        private var sum: BigDecimal = BigDecimal.ZERO

        fun add(value: BigDecimal) {
            window.addLast(value)
            sum = sum.add(value)
            if (window.size > windowSize) {
                sum = sum.subtract(window.removeFirst())
            }
        }

        /** まだ 1 件も記録が無ければ null。 */
        fun currentValue(): BigDecimal? =
            if (window.isEmpty()) null
            else sum.divide(BigDecimal.valueOf(window.size.toLong()), 2, RoundingMode.HALF_UP)
    }

    private companion object {
        /** 体重移動平均の窓幅（日）。 */
        const val WEIGHT_MOVING_AVERAGE_DAYS = 7
    }
}
