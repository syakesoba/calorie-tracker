package com.example.calorie.goal

import com.example.calorie.body.BodyRecordRepository
import com.example.calorie.common.ApiException
import com.example.calorie.goal.dto.GoalRequest
import com.example.calorie.goal.dto.GoalResponse
import com.example.calorie.goal.dto.GoalSuggestionResponse
import com.example.calorie.profile.ProfileService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Clock
import java.time.LocalDate

@Service
class GoalService(
    private val goalRepository: GoalRepository,
    private val bodyRecordRepository: BodyRecordRepository,
    private val profileService: ProfileService,
    private val clock: Clock,
) {

    /**
     * 目標を計算して提案する。保存はしない。
     *
     * 提案と保存を分けているのは、算出結果をそのまま押し付けず、
     * ユーザーが数値を見てから調整できるようにするため。
     */
    @Transactional(readOnly = true)
    fun suggest(userId: Long, paceKgPerMonth: BigDecimal): GoalSuggestionResponse {
        val today = LocalDate.now(clock)
        val profile = profileService.requireProfile(userId)

        val latestWeight = bodyRecordRepository
            .findFirstByUserIdAndRecordedOnLessThanEqualOrderByRecordedOnDesc(userId, today)
            ?: throw ApiException.conflict(
                "WEIGHT_REQUIRED", "目標を算出するには、先に体重を記録してください。"
            )

        val result = GoalCalculator.calculate(
            GoalCalculator.Input(
                sex = profile.sex,
                age = profile.ageOn(today),
                heightCm = profile.heightCm,
                weightKg = latestWeight.weightKg,
                activityLevel = profile.activityLevel,
                paceKgPerMonth = paceKgPerMonth,
            )
        )

        return GoalSuggestionResponse.from(result, paceKgPerMonth, latestWeight.weightKg)
    }

    @Transactional(readOnly = true)
    fun getCurrent(userId: Long): GoalResponse {
        val goal = goalRepository
            .findFirstByUserIdAndStartOnLessThanEqualOrderByStartOnDesc(userId, LocalDate.now(clock))
            ?: throw ApiException.notFound("GOAL_NOT_FOUND", "目標が未設定です。")
        return GoalResponse.from(goal)
    }

    /**
     * 目標を確定して保存する。
     *
     * 同じ開始日の目標があれば上書きし、無ければ新しい行を作る。
     * 古い目標は消さない（過去の達成率を後から計算し直せるようにするため）。
     */
    @Transactional
    fun put(userId: Long, request: GoalRequest): GoalResponse {
        val startOn = request.startOn ?: LocalDate.now(clock)
        val existing = goalRepository.findByUserIdAndStartOn(userId, startOn)

        val goal = if (existing != null) {
            existing.update(
                request.targetKcal, request.targetProteinG,
                request.targetFatG, request.targetCarbG, request.paceKgPerMonth,
            )
            existing
        } else {
            goalRepository.save(
                Goal.create(
                    userId = userId,
                    startOn = startOn,
                    targetKcal = request.targetKcal,
                    proteinG = request.targetProteinG,
                    fatG = request.targetFatG,
                    carbG = request.targetCarbG,
                    paceKgPerMonth = request.paceKgPerMonth,
                )
            )
        }

        return GoalResponse.from(goal)
    }
}
