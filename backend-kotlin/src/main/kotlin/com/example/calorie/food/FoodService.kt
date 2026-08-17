package com.example.calorie.food

import com.example.calorie.food.dto.FoodCreateRequest
import com.example.calorie.food.dto.FoodResponse
import org.springframework.data.domain.Limit
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class FoodService(private val foodRepository: FoodRepository) {

    @Transactional(readOnly = true)
    fun search(query: String, limit: Int, userId: Long): List<FoodResponse> =
        foodRepository.search(query.trim(), userId, Limit.of(limit))
            .map(FoodResponse::from)

    @Transactional
    fun createUserFood(request: FoodCreateRequest, userId: Long): FoodResponse {
        val food = Food.createByUser(
            userId = userId,
            name = request.name.trim(),
            nameKana = request.nameKana,
            category = request.category,
            per100g = request.nutritionPer100g.toNutrition(),
        )
        return FoodResponse.from(foodRepository.save(food))
    }
}
