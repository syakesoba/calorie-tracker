package com.example.calorie.goal

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 目標カロリーと目標 PFC。
 *
 * 履歴として複数行を持ち、`start_on` で「その日に有効な目標」を引く。
 * 上書きせず履歴にするのは、過去の達成率を後から計算し直せるようにするため。
 * 目標を変えた瞬間に過去の評価まで変わってしまうのは正しくない。
 */
@Entity
@Table(name = "goals")
class Goal protected constructor() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0
        protected set

    @Column(name = "start_on", nullable = false)
    lateinit var startOn: LocalDate
        protected set

    @Column(name = "target_kcal", nullable = false)
    var targetKcal: Int = 0
        protected set

    @Column(name = "target_protein_g", nullable = false, precision = 6, scale = 1)
    lateinit var targetProteinG: BigDecimal
        protected set

    @Column(name = "target_fat_g", nullable = false, precision = 6, scale = 1)
    lateinit var targetFatG: BigDecimal
        protected set

    @Column(name = "target_carb_g", nullable = false, precision = 6, scale = 1)
    lateinit var targetCarbG: BigDecimal
        protected set

    @Column(name = "pace_kg_per_month", precision = 4, scale = 2)
    var paceKgPerMonth: BigDecimal? = null
        protected set

    @Column(name = "created_at", nullable = false)
    lateinit var createdAt: OffsetDateTime
        protected set

    /** 同じ開始日の目標を上書きする場合に使う。 */
    fun update(
        targetKcal: Int,
        proteinG: BigDecimal,
        fatG: BigDecimal,
        carbG: BigDecimal,
        paceKgPerMonth: BigDecimal?,
    ) {
        this.targetKcal = targetKcal
        this.targetProteinG = proteinG
        this.targetFatG = fatG
        this.targetCarbG = carbG
        this.paceKgPerMonth = paceKgPerMonth
    }

    companion object {
        fun create(
            userId: Long,
            startOn: LocalDate,
            targetKcal: Int,
            proteinG: BigDecimal,
            fatG: BigDecimal,
            carbG: BigDecimal,
            paceKgPerMonth: BigDecimal?,
        ): Goal = Goal().apply {
            this.userId = userId
            this.startOn = startOn
            this.createdAt = OffsetDateTime.now()
            update(targetKcal, proteinG, fatG, carbG, paceKgPerMonth)
        }
    }
}
