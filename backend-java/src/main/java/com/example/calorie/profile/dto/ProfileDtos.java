package com.example.calorie.profile.dto;

import com.example.calorie.profile.ActivityLevel;
import com.example.calorie.profile.Sex;
import com.example.calorie.profile.UserProfile;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class ProfileDtos {

    private ProfileDtos() {
    }

    public record ProfileRequest(
            @NotNull Sex sex,
            @NotNull @Past LocalDate birthDate,
            @NotNull @DecimalMin("50") @DecimalMax("250") BigDecimal heightCm,
            @NotNull ActivityLevel activityLevel
    ) {
    }

    /**
     * @param age 生年月日から算出した現在の年齢。クライアントに計算させない。
     */
    public record ProfileResponse(
            Sex sex,
            LocalDate birthDate,
            BigDecimal heightCm,
            ActivityLevel activityLevel,
            int age
    ) {
        public static ProfileResponse from(UserProfile profile, LocalDate today) {
            return new ProfileResponse(
                    profile.getSex(),
                    profile.getBirthDate(),
                    profile.getHeightCm(),
                    profile.getActivityLevel(),
                    profile.ageOn(today)
            );
        }
    }
}
