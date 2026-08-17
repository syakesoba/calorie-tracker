package com.example.calorie.goal;

import com.example.calorie.goal.dto.GoalDtos.GoalRequest;
import com.example.calorie.goal.dto.GoalDtos.GoalResponse;
import com.example.calorie.goal.dto.GoalDtos.GoalSuggestionResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/goals")
@Validated
public class GoalController {

    private final GoalService goalService;

    public GoalController(GoalService goalService) {
        this.goalService = goalService;
    }

    @GetMapping("/suggestion")
    public GoalSuggestionResponse suggestion(
            @RequestParam(defaultValue = "2") @DecimalMin("0") @DecimalMax("4") BigDecimal paceKgPerMonth,
            @AuthenticationPrincipal Long userId) {
        return goalService.suggest(userId, paceKgPerMonth);
    }

    @GetMapping("/current")
    public GoalResponse current(@AuthenticationPrincipal Long userId) {
        return goalService.getCurrent(userId);
    }

    @PutMapping
    public GoalResponse put(@Valid @RequestBody GoalRequest request,
                            @AuthenticationPrincipal Long userId) {
        return goalService.put(userId, request);
    }
}
