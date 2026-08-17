package com.example.calorie.meal

import com.example.calorie.common.ApiException
import com.example.calorie.food.FoodRepository
import com.example.calorie.meal.dto.MealLogRequest
import com.example.calorie.meal.dto.MealLogResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class MealLogService(
    private val mealLogRepository: MealLogRepository,
    private val foodRepository: FoodRepository,
) {

    @Transactional(readOnly = true)
    fun listByDate(userId: Long, date: LocalDate): List<MealLogResponse> =
        mealLogRepository.findByUserIdAndDate(userId, date).map(MealLogResponse::from)

    @Transactional
    fun create(userId: Long, request: MealLogRequest): MealLogResponse {
        // 食品はまとめて 1 回で引く。品目ごとに引くと、10 品の記録で 10 回問い合わせることになる。
        val foodIds = request.items.map { it.foodId }.distinct()
        val foods = foodRepository.findAllAccessibleByIds(foodIds, userId).associateBy { it.id }

        val log = MealLog.create(userId, request.eatenOn, request.mealType, request.note)

        for (item in request.items) {
            // 存在しない食品と、他人が登録した食品を区別しない。
            // 区別すると「その ID は存在する」ことを教えてしまうため。
            val food = foods[item.foodId]
                ?: throw ApiException.notFound("FOOD_NOT_FOUND", "指定された食品が見つかりません。")
            log.addItem(food, item.amountG)
        }

        return MealLogResponse.from(mealLogRepository.save(log))
    }

    /**
     * 削除。他人の記録を指定した場合は 403 ではなく 404 を返す。
     * 403 だと「その ID は存在する」ことを教えてしまうため。
     */
    @Transactional
    fun delete(userId: Long, id: Long) {
        val log = mealLogRepository.findByIdAndUserId(id, userId)
            ?: throw ApiException.notFound("NOT_FOUND", "対象が見つかりません。")
        mealLogRepository.delete(log)
    }
}
