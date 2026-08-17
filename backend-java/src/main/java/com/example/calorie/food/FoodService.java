package com.example.calorie.food;

import com.example.calorie.food.dto.FoodDtos.FoodCreateRequest;
import com.example.calorie.food.dto.FoodDtos.FoodResponse;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class FoodService {

    private final FoodRepository foodRepository;

    public FoodService(FoodRepository foodRepository) {
        this.foodRepository = foodRepository;
    }

    @Transactional(readOnly = true)
    public List<FoodResponse> search(String query, int limit, Long userId) {
        return foodRepository.search(query.trim(), userId, Limit.of(limit)).stream()
                .map(FoodResponse::from)
                .toList();
    }

    @Transactional
    public FoodResponse createUserFood(FoodCreateRequest request, Long userId) {
        Food food = Food.createByUser(
                userId,
                request.name().trim(),
                request.nameKana(),
                request.category(),
                request.nutritionPer100g().toNutrition()
        );
        return FoodResponse.from(foodRepository.save(food));
    }
}
