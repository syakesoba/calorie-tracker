package com.example.calorie.summary

import com.example.calorie.summary.dto.DailySummaryResponse
import com.example.calorie.summary.dto.RangeSummaryResponse
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.LocalDate

@RestController
@RequestMapping("/api/summaries")
class SummaryController(private val service: SummaryService) {

    @GetMapping("/daily")
    fun daily(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) date: LocalDate,
        @AuthenticationPrincipal userId: Long,
    ): DailySummaryResponse = service.daily(userId, date)

    @GetMapping("/range")
    fun range(
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) from: LocalDate,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) to: LocalDate,
        @AuthenticationPrincipal userId: Long,
    ): RangeSummaryResponse = service.range(userId, from, to)
}
