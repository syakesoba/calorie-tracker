package com.example.calorie.profile

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.Period

/**
 * 性別。
 *
 * Mifflin-St Jeor 式が男女で異なる定数を使うため、この 2 値のみを扱う。
 * これは計算式の制約であり、人の在り方についての表明ではない。
 */
enum class Sex { MALE, FEMALE }

/**
 * 活動レベル。TDEE を求めるときに BMR に掛ける係数を持つ。
 *
 * Kotlin の enum はコンストラクタ引数をそのままプロパティにできるため、
 * Java 側で必要だったフィールド宣言と getter が要らない。
 */
enum class ActivityLevel(val factor: BigDecimal) {
    /** ほぼ座位。 */
    SEDENTARY(BigDecimal("1.2")),

    /** 軽い運動（週 1〜3 回）。 */
    LIGHT(BigDecimal("1.375")),

    /** 中程度の運動（週 3〜5 回）。 */
    MODERATE(BigDecimal("1.55")),

    /** 激しい運動（週 6〜7 回）。 */
    ACTIVE(BigDecimal("1.725")),

    /** 非常に激しい運動、または肉体労働。 */
    VERY_ACTIVE(BigDecimal("1.9")),
}

/**
 * 身体情報。users と 1 対 1 で、user_id をそのまま主キーにしている。
 *
 * 体重はここに持たない。日々変わるものであり、固定値として持つと実態とずれるため、
 * body_records の最新値を使う。
 */
@Entity
@Table(name = "user_profiles")
class UserProfile protected constructor() {

    @Id
    @Column(name = "user_id")
    var userId: Long = 0
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "sex", nullable = false, length = 10)
    lateinit var sex: Sex
        protected set

    @Column(name = "birth_date", nullable = false)
    lateinit var birthDate: LocalDate
        protected set

    @Column(name = "height_cm", nullable = false, precision = 4, scale = 1)
    lateinit var heightCm: BigDecimal
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "activity_level", nullable = false, length = 20)
    lateinit var activityLevel: ActivityLevel
        protected set

    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: OffsetDateTime
        protected set

    fun update(sex: Sex, birthDate: LocalDate, heightCm: BigDecimal, activityLevel: ActivityLevel) {
        this.sex = sex
        this.birthDate = birthDate
        this.heightCm = heightCm
        this.activityLevel = activityLevel
        this.updatedAt = OffsetDateTime.now()
    }

    /**
     * 指定日時点の年齢。
     *
     * クライアントに計算させず、サーバーで求めて返す。Web とモバイルで端末の
     * タイムゾーンや実装が異なると、同じ人の年齢が食い違いうるため。
     */
    fun ageOn(date: LocalDate): Int = Period.between(birthDate, date).years

    @PreUpdate
    protected fun onUpdate() {
        updatedAt = OffsetDateTime.now()
    }

    companion object {
        fun create(
            userId: Long,
            sex: Sex,
            birthDate: LocalDate,
            heightCm: BigDecimal,
            activityLevel: ActivityLevel,
        ): UserProfile = UserProfile().apply {
            this.userId = userId
            update(sex, birthDate, heightCm, activityLevel)
        }
    }
}
