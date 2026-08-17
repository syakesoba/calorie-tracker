package com.example.calorie.profile;

import com.example.calorie.common.ApiException;
import com.example.calorie.profile.dto.ProfileDtos.ProfileRequest;
import com.example.calorie.profile.dto.ProfileDtos.ProfileResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

@Service
public class ProfileService {

    private final UserProfileRepository profileRepository;
    private final Clock clock;

    public ProfileService(UserProfileRepository profileRepository, Clock clock) {
        this.profileRepository = profileRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ProfileResponse get(Long userId) {
        UserProfile profile = profileRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound(
                        "PROFILE_NOT_FOUND", "プロフィールが未設定です。"));
        return ProfileResponse.from(profile, LocalDate.now(clock));
    }

    /** 未登録なら作成、登録済みなら更新する。 */
    @Transactional
    public ProfileResponse upsert(Long userId, ProfileRequest request) {
        UserProfile profile = profileRepository.findById(userId)
                .map(existing -> {
                    existing.update(request.sex(), request.birthDate(),
                            request.heightCm(), request.activityLevel());
                    return existing;
                })
                .orElseGet(() -> profileRepository.save(UserProfile.create(
                        userId, request.sex(), request.birthDate(),
                        request.heightCm(), request.activityLevel())));

        return ProfileResponse.from(profile, LocalDate.now(clock));
    }

    /** 目標算出など、他のサービスからプロフィールを必要とする場合の入口。 */
    @Transactional(readOnly = true)
    public UserProfile requireProfile(Long userId) {
        return profileRepository.findById(userId)
                .orElseThrow(() -> ApiException.conflict(
                        "PROFILE_REQUIRED",
                        "目標を算出するには、先にプロフィールと体重を登録してください。"));
    }
}
