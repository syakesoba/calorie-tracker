package com.example.calorie.goal

import com.example.calorie.goal.dto.GoalRequest
import com.example.calorie.goal.dto.GoalResponse
import com.example.calorie.goal.dto.GoalSuggestionResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal

@RestController
@RequestMapping("/api/goals")
@Validated
class GoalController(private val goalService: GoalService) {

    @GetMapping("/suggestion")
    fun suggestion(
        @RequestParam(defaultValue = "2")
        @DecimalMin("0") @DecimalMax("4")
        paceKgPerMonth: BigDecimal,
        @AuthenticationPrincipal userId: Long,
    ): GoalSuggestionResponse = goalService.suggest(userId, paceKgPerMonth)

    @GetMapping("/current")
    fun current(@AuthenticationPrincipal userId: Long): GoalResponse =
        goalService.getCurrent(userId)

    @PutMapping
    fun put(
        @Valid @RequestBody request: GoalRequest,
        @AuthenticationPrincipal userId: Long,
    ): GoalResponse = goalService.put(userId, request)
}
