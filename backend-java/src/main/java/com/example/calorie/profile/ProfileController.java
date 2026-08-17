package com.example.calorie.profile;

import com.example.calorie.profile.dto.ProfileDtos.ProfileRequest;
import com.example.calorie.profile.dto.ProfileDtos.ProfileResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/profile")
public class ProfileController {

    private final ProfileService profileService;

    public ProfileController(ProfileService profileService) {
        this.profileService = profileService;
    }

    @GetMapping
    public ProfileResponse get(@AuthenticationPrincipal Long userId) {
        return profileService.get(userId);
    }

    @PutMapping
    public ProfileResponse put(@Valid @RequestBody ProfileRequest request,
                               @AuthenticationPrincipal Long userId) {
        return profileService.upsert(userId, request);
    }
}
