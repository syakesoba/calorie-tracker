package com.example.calorie.meal;

import com.example.calorie.common.ApiException;
import com.example.calorie.food.Food;
import com.example.calorie.food.FoodRepository;
import com.example.calorie.meal.dto.MealDtos.MealLogItemRequest;
import com.example.calorie.meal.dto.MealDtos.MealLogRequest;
import com.example.calorie.meal.dto.MealDtos.MealLogResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Service
public class MealLogService {

    private final MealLogRepository mealLogRepository;
    private final FoodRepository foodRepository;

    public MealLogService(MealLogRepository mealLogRepository, FoodRepository foodRepository) {
        this.mealLogRepository = mealLogRepository;
        this.foodRepository = foodRepository;
    }

    @Transactional(readOnly = true)
    public List<MealLogResponse> listByDate(Long userId, java.time.LocalDate date) {
        return mealLogRepository.findByUserIdAndDate(userId, date).stream()
                .map(MealLogResponse::from)
                .toList();
    }

    @Transactional
    public MealLogResponse create(Long userId, MealLogRequest request) {
        // 食品はまとめて 1 回で引く。品目ごとに引くと、10 品の記録で 10 回問い合わせることになる。
        List<Long> foodIds = request.items().stream().map(MealLogItemRequest::foodId).distinct().toList();
        Map<Long, Food> foods = foodRepository.findAllAccessibleByIds(foodIds, userId).stream()
                .collect(java.util.stream.Collectors.toMap(Food::getId, Function.identity()));

        MealLog log = MealLog.create(userId, request.eatenOn(), request.mealType(), request.note());

        for (MealLogItemRequest item : request.items()) {
            Food food = foods.get(item.foodId());
            if (food == null) {
                // 存在しない食品と、他人が登録した食品を区別しない。
                // 区別すると「その ID は存在する」ことを教えてしまうため。
                throw ApiException.notFound("FOOD_NOT_FOUND", "指定された食品が見つかりません。");
            }
            log.addItem(food, item.amountG());
        }

        return MealLogResponse.from(mealLogRepository.save(log));
    }

    /**
     * 削除。他人の記録を指定した場合は 403 ではなく 404 を返す。
     * 403 だと「その ID は存在する」ことを教えてしまうため。
     */
    @Transactional
    public void delete(Long userId, Long id) {
        MealLog log = mealLogRepository.findByIdAndUserId(id, userId)
                .orElseThrow(() -> ApiException.notFound("NOT_FOUND", "対象が見つかりません。"));
        mealLogRepository.delete(log);
    }
}
