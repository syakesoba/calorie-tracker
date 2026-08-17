package com.example.calorie.body

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 体重・体脂肪率の記録。1 ユーザー 1 日 1 レコード（DB のユニーク制約で担保）。
 */
@Entity
@Table(name = "body_records")
class BodyRecord protected constructor() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0
        protected set

    @Column(name = "recorded_on", nullable = false)
    lateinit var recordedOn: LocalDate
        protected set

    @Column(name = "weight_kg", nullable = false, precision = 5, scale = 2)
    lateinit var weightKg: BigDecimal
        protected set

    @Column(name = "body_fat_pct", precision = 4, scale = 1)
    var bodyFatPct: BigDecimal? = null
        protected set

    @Column(name = "created_at", nullable = false)
    lateinit var createdAt: OffsetDateTime
        protected set

    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: OffsetDateTime
        protected set

    fun update(weightKg: BigDecimal, bodyFatPct: BigDecimal?) {
        this.weightKg = weightKg
        this.bodyFatPct = bodyFatPct
        this.updatedAt = OffsetDateTime.now()
    }

    @PreUpdate
    protected fun onUpdate() {
        updatedAt = OffsetDateTime.now()
    }

    companion object {
        fun create(
            userId: Long,
            recordedOn: LocalDate,
            weightKg: BigDecimal,
            bodyFatPct: BigDecimal?,
        ): BodyRecord = BodyRecord().apply {
            this.userId = userId
            this.recordedOn = recordedOn
            this.weightKg = weightKg
            this.bodyFatPct = bodyFatPct
            val now = OffsetDateTime.now()
            this.createdAt = now
            this.updatedAt = now
        }
    }
}
