package com.example.calorie.meal

import com.example.calorie.common.Nutrition
import com.example.calorie.food.Food
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.FetchType
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.OrderBy
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

/**
 * 食事区分。列挙の順序がそのまま画面と集計の表示順になる。
 */
enum class MealType { BREAKFAST, LUNCH, DINNER, SNACK }

/**
 * 1 回の食事の記録。「2026-08-17 の朝食」が 1 レコードで、その中に品目が並ぶ。
 */
@Entity
@Table(name = "meal_logs")
class MealLog protected constructor() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @Column(name = "user_id", nullable = false)
    var userId: Long = 0
        protected set

    @Column(name = "eaten_on", nullable = false)
    lateinit var eatenOn: LocalDate
        protected set

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 20)
    lateinit var mealType: MealType
        protected set

    @Column(name = "note", length = 500)
    var note: String? = null
        protected set

    @OneToMany(mappedBy = "mealLog", cascade = [CascadeType.ALL], orphanRemoval = true)
    @OrderBy("sortOrder asc")
    private val mutableItems: MutableList<MealLogItem> = mutableListOf()

    /** 外から書き換えられないよう読み取り専用のビューを返す。 */
    val items: List<MealLogItem> get() = mutableItems.toList()

    @Column(name = "created_at", nullable = false)
    lateinit var createdAt: OffsetDateTime
        protected set

    @Column(name = "updated_at", nullable = false)
    lateinit var updatedAt: OffsetDateTime
        protected set

    /**
     * 品目を追加する。栄養値はこの時点のマスタ値から換算して確定する。
     *
     * @param amountG 分量（グラム）
     */
    fun addItem(food: Food, amountG: BigDecimal) {
        mutableItems.add(MealLogItem.snapshot(this, food, amountG, mutableItems.size))
    }

    /**
     * この食事の合計。品目のスナップショットを足し合わせるだけで、
     * 食品マスタは参照しない。
     */
    fun total(): Nutrition =
        mutableItems.fold(Nutrition.ZERO) { acc, item -> acc + item.nutrition() }

    @PreUpdate
    protected fun onUpdate() {
        updatedAt = OffsetDateTime.now()
    }

    companion object {
        fun create(userId: Long, eatenOn: LocalDate, mealType: MealType, note: String?): MealLog =
            MealLog().apply {
                this.userId = userId
                this.eatenOn = eatenOn
                this.mealType = mealType
                this.note = note
                val now = OffsetDateTime.now()
                this.createdAt = now
                this.updatedAt = now
            }
    }
}

/**
 * 食事記録の 1 品。
 *
 * **栄養値は登録時点のスナップショットである。** 食品マスタへの参照（[foodId]）も
 * 持つが、栄養値の算出にそれを使ってはならない。食品マスタは成分表の改訂や
 * ユーザーの修正で変わるが、「その日に何 kcal 食べたか」という記録は後から
 * 変わってはならないため。
 *
 * これは正規化のセオリーから外れた意図的な非正規化であり、削ってはならない。
 */
@Entity
@Table(name = "meal_log_items")
class MealLogItem protected constructor() {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null
        protected set

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meal_log_id", nullable = false)
    lateinit var mealLog: MealLog
        protected set

    /** 由来の食品。マスタから削除されると null になるが、記録自体は残る。 */
    @Column(name = "food_id")
    var foodId: Long? = null
        protected set

    @Column(name = "food_name", nullable = false, length = 200)
    lateinit var foodName: String
        protected set

    @Column(name = "amount_g", nullable = false, precision = 8, scale = 2)
    lateinit var amountG: BigDecimal
        protected set

    @Column(name = "kcal", nullable = false, precision = 8, scale = 2)
    lateinit var kcal: BigDecimal
        protected set

    @Column(name = "protein_g", nullable = false, precision = 8, scale = 2)
    lateinit var proteinG: BigDecimal
        protected set

    @Column(name = "fat_g", nullable = false, precision = 8, scale = 2)
    lateinit var fatG: BigDecimal
        protected set

    @Column(name = "carb_g", nullable = false, precision = 8, scale = 2)
    lateinit var carbG: BigDecimal
        protected set

    @Column(name = "salt_g", precision = 8, scale = 3)
    var saltG: BigDecimal? = null
        protected set

    @Column(name = "fiber_g", precision = 8, scale = 2)
    var fiberG: BigDecimal? = null
        protected set

    @Column(name = "sugar_g", precision = 8, scale = 2)
    var sugarG: BigDecimal? = null
        protected set

    @Column(name = "sort_order", nullable = false)
    var sortOrder: Int = 0
        protected set

    fun nutrition(): Nutrition = Nutrition(kcal, proteinG, fatG, carbG, saltG, fiberG, sugarG)

    companion object {
        /**
         * 食品と分量からスナップショットを作る。
         *
         * 換算はここでのみ行う。クライアントに計算させないのは、計算式が
         * クライアントとサーバーに二重に存在すると、Web とモバイルで結果が
         * ずれる余地が生まれるため。
         */
        fun snapshot(mealLog: MealLog, food: Food, amountG: BigDecimal, sortOrder: Int): MealLogItem {
            val nutrition = food.nutritionPer100g().forAmountGrams(amountG)
            return MealLogItem().apply {
                this.mealLog = mealLog
                this.foodId = food.id
                this.foodName = food.name
                this.amountG = amountG
                this.sortOrder = sortOrder
                this.kcal = nutrition.kcal
                this.proteinG = nutrition.proteinG
                this.fatG = nutrition.fatG
                this.carbG = nutrition.carbG
                this.saltG = nutrition.saltG
                this.fiberG = nutrition.fiberG
                this.sugarG = nutrition.sugarG
            }
        }
    }
}
