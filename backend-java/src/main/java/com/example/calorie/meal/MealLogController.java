package com.example.calorie.meal;

import com.example.calorie.meal.dto.MealDtos.MealLogRequest;
import com.example.calorie.meal.dto.MealDtos.MealLogResponse;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/meal-logs")
public class MealLogController {

    private final MealLogService service;

    public MealLogController(MealLogService service) {
        this.service = service;
    }

    @GetMapping
    public List<MealLogResponse> list(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @AuthenticationPrincipal Long userId) {
        return service.listByDate(userId, date);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MealLogResponse create(@Valid @RequestBody MealLogRequest request,
                                  @AuthenticationPrincipal Long userId) {
        return service.create(userId, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id, @AuthenticationPrincipal Long userId) {
        service.delete(userId, id);
    }
}
