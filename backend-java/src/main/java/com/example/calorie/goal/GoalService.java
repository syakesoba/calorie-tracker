package com.example.calorie.goal;

import com.example.calorie.body.BodyRecord;
import com.example.calorie.body.BodyRecordRepository;
import com.example.calorie.common.ApiException;
import com.example.calorie.goal.dto.GoalDtos.GoalRequest;
import com.example.calorie.goal.dto.GoalDtos.GoalResponse;
import com.example.calorie.goal.dto.GoalDtos.GoalSuggestionResponse;
import com.example.calorie.profile.ProfileService;
import com.example.calorie.profile.UserProfile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

@Service
public class GoalService {

    private final GoalRepository goalRepository;
    private final BodyRecordRepository bodyRecordRepository;
    private final ProfileService profileService;
    private final Clock clock;

    public GoalService(GoalRepository goalRepository,
                       BodyRecordRepository bodyRecordRepository,
                       ProfileService profileService,
                       Clock clock) {
        this.goalRepository = goalRepository;
        this.bodyRecordRepository = bodyRecordRepository;
        this.profileService = profileService;
        this.clock = clock;
    }

    /**
     * 目標を計算して提案する。保存はしない。
     *
     * <p>提案と保存を分けているのは、算出結果をそのまま押し付けず、
     * ユーザーが数値を見てから調整できるようにするため。
     */
    @Transactional(readOnly = true)
    public GoalSuggestionResponse suggest(Long userId, BigDecimal paceKgPerMonth) {
        LocalDate today = LocalDate.now(clock);
        UserProfile profile = profileService.requireProfile(userId);

        BodyRecord latestWeight = bodyRecordRepository
                .findFirstByUserIdAndRecordedOnLessThanEqualOrderByRecordedOnDesc(userId, today)
                .orElseThrow(() -> ApiException.conflict(
                        "WEIGHT_REQUIRED",
                        "目標を算出するには、先に体重を記録してください。"));

        GoalCalculator.Result result = GoalCalculator.calculate(new GoalCalculator.Input(
                profile.getSex(),
                profile.ageOn(today),
                profile.getHeightCm(),
                latestWeight.getWeightKg(),
                profile.getActivityLevel(),
                paceKgPerMonth
        ));

        return GoalSuggestionResponse.from(result, paceKgPerMonth, latestWeight.getWeightKg());
    }

    @Transactional(readOnly = true)
    public GoalResponse getCurrent(Long userId) {
        return goalRepository
                .findFirstByUserIdAndStartOnLessThanEqualOrderByStartOnDesc(userId, LocalDate.now(clock))
                .map(GoalResponse::from)
                .orElseThrow(() -> ApiException.notFound("GOAL_NOT_FOUND", "目標が未設定です。"));
    }

    /**
     * 目標を確定して保存する。
     *
     * <p>同じ開始日の目標があれば上書きし、無ければ新しい行を作る。
     * 古い目標は消さない（過去の達成率を後から計算し直せるようにするため）。
     */
    @Transactional
    public GoalResponse put(Long userId, GoalRequest request) {
        LocalDate startOn = request.startOn() != null ? request.startOn() : LocalDate.now(clock);

        Goal goal = goalRepository.findByUserIdAndStartOn(userId, startOn)
                .map(existing -> {
                    existing.update(request.targetKcal(), request.targetProteinG(),
                            request.targetFatG(), request.targetCarbG(), request.paceKgPerMonth());
                    return existing;
                })
                .orElseGet(() -> goalRepository.save(Goal.create(
                        userId, startOn, request.targetKcal(), request.targetProteinG(),
                        request.targetFatG(), request.targetCarbG(), request.paceKgPerMonth())));

        return GoalResponse.from(goal);
    }
}
