package com.example.calorie.food

import com.example.calorie.common.Nutrition
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.OffsetDateTime

/**
 * 食品データの出所。
 *
 * 出所を区別しておくことで、後から「どの値が検証済みか」を判別できる。
 * 名前を偽ってはならない。
 */
enum class FoodSource {
    /** Phase 1 で投入した代表値。おおよその値であり、正確な栄養計算には使えない。 */
    SEED,

    /** 日本食品標準成分表から取り込んだ値（Phase 3）。 */
    MEXT,

    /** Open Food Facts 由来（Phase 3）。 */
    OFF,

    /** ユーザーが手動登録した食品。登録者本人にのみ見える。 */
    USER,
}

/**
 * 食品マスタ。栄養値はすべて可食部 100g あたりで保持する。
 *
 * 分量に応じた換算はここでは行わず、記録を作る時点で [Nutrition.forAmountGrams] を
 * 通してスナップショットを作る。
 */
@Entity
@Table(name = "foods")
class Food protected constructor() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    lateinit var source: FoodSource
        protected set

    @Column(name = "source_code", length = 64)
    var sourceCode: String? = null
        protected set

    @Column(name = "name", nullable = false, length = 200)
    lateinit var name: String
        protected set

    @Column(name = "name_kana", length = 200)
    var nameKana: String? = null
        protected set

    @Column(name = "category", length = 100)
    var category: String? = null
        protected set

    @Column(name = "kcal", nullable = false, precision = 7, scale = 2)
    lateinit var kcal: BigDecimal
        protected set

    @Column(name = "protein_g", nullable = false, precision = 7, scale = 2)
    lateinit var proteinG: BigDecimal
        protected set

    @Column(name = "fat_g", nullable = false, precision = 7, scale = 2)
    lateinit var fatG: BigDecimal
        protected set

    @Column(name = "carb_g", nullable = false, precision = 7, scale = 2)
    lateinit var carbG: BigDecimal
        protected set

    @Column(name = "salt_g", precision = 7, scale = 3)
    var saltG: BigDecimal? = null
        protected set

    @Column(name = "fiber_g", precision = 7, scale = 2)
    var fiberG: BigDecimal? = null
        protected set

    @Column(name = "sugar_g", precision = 7, scale = 2)
    var sugarG: BigDecimal? = null
        protected set

    /** USER 登録の場合の作成者。共有マスタ由来の食品は null。 */
    @Column(name = "created_by_user_id")
    var createdByUserId: Long? = null
        protected set

    @Column(name = "created_at", nullable = false)
    lateinit var createdAt: OffsetDateTime
        protected set

    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: OffsetDateTime
        protected set

    /** 可食部 100g あたりの栄養値。 */
    fun nutritionPer100g(): Nutrition =
        Nutrition(kcal, proteinG, fatG, carbG, saltG, fiberG, sugarG)

    companion object {
        /**
         * ユーザーが独自に登録する食品を生成する。
         *
         * 共有マスタ（SEED / MEXT / OFF）は Flyway と取り込みバッチが作るため、
         * アプリケーションから生成できるのは USER のみ。
         */
        fun createByUser(
            userId: Long,
            name: String,
            nameKana: String?,
            category: String?,
            per100g: Nutrition,
        ): Food = Food().apply {
            this.source = FoodSource.USER
            this.createdByUserId = userId
            this.name = name
            this.nameKana = nameKana
            this.category = category
            this.kcal = per100g.kcal
            this.proteinG = per100g.proteinG
            this.fatG = per100g.fatG
            this.carbG = per100g.carbG
            this.saltG = per100g.saltG
            this.fiberG = per100g.fiberG
            this.sugarG = per100g.sugarG
            val now = OffsetDateTime.now()
            this.createdAt = now
            this.updatedAt = now
        }
    }
}
