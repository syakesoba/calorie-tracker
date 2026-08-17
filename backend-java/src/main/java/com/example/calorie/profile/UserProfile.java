package com.example.calorie.profile;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.Period;

/**
 * 身体情報。users と 1 対 1 で、user_id をそのまま主キーにしている。
 *
 * <p>体重はここに持たない。日々変わるものであり、固定値として持つと実態とずれるため、
 * body_records の最新値を使う。
 */
@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sex", nullable = false, length = 10)
    private Sex sex;

    @Column(name = "birth_date", nullable = false)
    private LocalDate birthDate;

    @Column(name = "height_cm", nullable = false, precision = 4, scale = 1)
    private BigDecimal heightCm;

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_level", nullable = false, length = 20)
    private ActivityLevel activityLevel;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected UserProfile() {
    }

    public static UserProfile create(Long userId, Sex sex, LocalDate birthDate,
                                     BigDecimal heightCm, ActivityLevel activityLevel) {
        UserProfile profile = new UserProfile();
        profile.userId = userId;
        profile.update(sex, birthDate, heightCm, activityLevel);
        return profile;
    }

    public void update(Sex sex, LocalDate birthDate, BigDecimal heightCm, ActivityLevel activityLevel) {
        this.sex = sex;
        this.birthDate = birthDate;
        this.heightCm = heightCm;
        this.activityLevel = activityLevel;
        this.updatedAt = OffsetDateTime.now();
    }

    /**
     * 指定日時点の年齢。
     *
     * <p>クライアントに計算させず、サーバーで求めて返す。Web とモバイルで
     * 端末のタイムゾーンや実装が異なると、同じ人の年齢が食い違いうるため。
     */
    public int ageOn(LocalDate date) {
        return Period.between(birthDate, date).getYears();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getUserId() {
        return userId;
    }

    public Sex getSex() {
        return sex;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public ActivityLevel getActivityLevel() {
        return activityLevel;
    }
}
