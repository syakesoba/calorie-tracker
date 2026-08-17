package com.example.calorie.profile

import com.example.calorie.profile.dto.ProfileRequest
import com.example.calorie.profile.dto.ProfileResponse
import jakarta.validation.Valid
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/profile")
class ProfileController(private val profileService: ProfileService) {

    @GetMapping
    fun get(@AuthenticationPrincipal userId: Long): ProfileResponse =
        profileService.get(userId)

    @PutMapping
    fun put(
        @Valid @RequestBody request: ProfileRequest,
        @AuthenticationPrincipal userId: Long,
    ): ProfileResponse = profileService.upsert(userId, request)
}
