package com.example.calorie.profile.dto

import com.example.calorie.profile.ActivityLevel
import com.example.calorie.profile.Sex
import com.example.calorie.profile.UserProfile
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.Past
import java.math.BigDecimal
import java.time.LocalDate

data class ProfileRequest(
    val sex: Sex,
    @field:Past val birthDate: LocalDate,
    @field:DecimalMin("50") @field:DecimalMax("250") val heightCm: BigDecimal,
    val activityLevel: ActivityLevel,
)

/**
 * @param age 生年月日から算出した現在の年齢。クライアントに計算させない。
 */
data class ProfileResponse(
    val sex: Sex,
    val birthDate: LocalDate,
    val heightCm: BigDecimal,
    val activityLevel: ActivityLevel,
    val age: Int,
) {
    companion object {
        fun from(profile: UserProfile, today: LocalDate) = ProfileResponse(
            sex = profile.sex,
            birthDate = profile.birthDate,
            heightCm = profile.heightCm,
            activityLevel = profile.activityLevel,
            age = profile.ageOn(today),
        )
    }
}
