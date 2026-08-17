package com.example.calorie.food

import com.example.calorie.food.dto.FoodCreateRequest
import com.example.calorie.food.dto.FoodResponse
import jakarta.validation.Valid
import jakarta.validation.constraints.Max
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/foods")
@Validated
class FoodController(private val foodService: FoodService) {

    @GetMapping
    fun search(
        @RequestParam @NotBlank @Size(max = 100) query: String,
        @RequestParam(defaultValue = "20") @Min(1) @Max(100) limit: Int,
        @AuthenticationPrincipal userId: Long,
    ): List<FoodResponse> = foodService.search(query, limit, userId)

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @Valid @RequestBody request: FoodCreateRequest,
        @AuthenticationPrincipal userId: Long,
    ): FoodResponse = foodService.createUserFood(request, userId)
}
