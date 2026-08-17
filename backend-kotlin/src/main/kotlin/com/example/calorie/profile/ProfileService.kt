package com.example.calorie.profile

import com.example.calorie.common.ApiException
import com.example.calorie.profile.dto.ProfileRequest
import com.example.calorie.profile.dto.ProfileResponse
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Clock
import java.time.LocalDate

@Service
class ProfileService(
    private val profileRepository: UserProfileRepository,
    private val clock: Clock,
) {

    @Transactional(readOnly = true)
    fun get(userId: Long): ProfileResponse {
        val profile = profileRepository.findById(userId)
            .orElseThrow { ApiException.notFound("PROFILE_NOT_FOUND", "プロフィールが未設定です。") }
        return ProfileResponse.from(profile, LocalDate.now(clock))
    }

    /** 未登録なら作成、登録済みなら更新する。 */
    @Transactional
    fun upsert(userId: Long, request: ProfileRequest): ProfileResponse {
        val existing = profileRepository.findById(userId).orElse(null)

        val profile = if (existing != null) {
            existing.update(request.sex, request.birthDate, request.heightCm, request.activityLevel)
            existing
        } else {
            profileRepository.save(
                UserProfile.create(
                    userId, request.sex, request.birthDate, request.heightCm, request.activityLevel
                )
            )
        }

        return ProfileResponse.from(profile, LocalDate.now(clock))
    }

    /** 目標算出など、他のサービスからプロフィールを必要とする場合の入口。 */
    @Transactional(readOnly = true)
    fun requireProfile(userId: Long): UserProfile =
        profileRepository.findById(userId).orElseThrow {
            ApiException.conflict(
                "PROFILE_REQUIRED",
                "目標を算出するには、先にプロフィールと体重を登録してください。",
            )
        }
}
