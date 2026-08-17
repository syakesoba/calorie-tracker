package com.example.calorie.meal;

import com.example.calorie.common.Nutrition;
import com.example.calorie.food.Food;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * 食事記録の 1 品。
 *
 * <p><strong>栄養値は登録時点のスナップショットである。</strong>食品マスタへの参照
 * （{@code foodId}）も持つが、栄養値の算出にそれを使ってはならない。食品マスタは
 * 成分表の改訂やユーザーの修正で変わるが、「その日に何 kcal 食べたか」という記録は
 * 後から変わってはならないため。
 *
 * <p>これは正規化のセオリーから外れた意図的な非正規化であり、整理のために
 * 削ってはならない。
 */
@Entity
@Table(name = "meal_log_items")
public class MealLogItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "meal_log_id", nullable = false)
    private MealLog mealLog;

    /** 由来の食品。マスタから削除されると null になるが、記録自体は残る。 */
    @Column(name = "food_id")
    private Long foodId;

    @Column(name = "food_name", nullable = false, length = 200)
    private String foodName;

    @Column(name = "amount_g", nullable = false, precision = 8, scale = 2)
    private BigDecimal amountG;

    @Column(name = "kcal", nullable = false, precision = 8, scale = 2)
    private BigDecimal kcal;

    @Column(name = "protein_g", nullable = false, precision = 8, scale = 2)
    private BigDecimal proteinG;

    @Column(name = "fat_g", nullable = false, precision = 8, scale = 2)
    private BigDecimal fatG;

    @Column(name = "carb_g", nullable = false, precision = 8, scale = 2)
    private BigDecimal carbG;

    @Column(name = "salt_g", precision = 8, scale = 3)
    private BigDecimal saltG;

    @Column(name = "fiber_g", precision = 8, scale = 2)
    private BigDecimal fiberG;

    @Column(name = "sugar_g", precision = 8, scale = 2)
    private BigDecimal sugarG;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected MealLogItem() {
    }

    /**
     * 食品と分量からスナップショットを作る。
     *
     * <p>換算はここでのみ行う。クライアントに計算させないのは、計算式が
     * クライアントとサーバーに二重に存在すると、Web とモバイルで結果が
     * ずれる余地が生まれるため。
     */
    static MealLogItem snapshot(MealLog mealLog, Food food, BigDecimal amountG, int sortOrder) {
        Nutrition nutrition = food.nutritionPer100g().forAmountGrams(amountG);

        MealLogItem item = new MealLogItem();
        item.mealLog = mealLog;
        item.foodId = food.getId();
        item.foodName = food.getName();
        item.amountG = amountG;
        item.sortOrder = sortOrder;
        item.kcal = nutrition.kcal();
        item.proteinG = nutrition.proteinG();
        item.fatG = nutrition.fatG();
        item.carbG = nutrition.carbG();
        item.saltG = nutrition.saltG();
        item.fiberG = nutrition.fiberG();
        item.sugarG = nutrition.sugarG();
        return item;
    }

    public Nutrition nutrition() {
        return new Nutrition(kcal, proteinG, fatG, carbG, saltG, fiberG, sugarG);
    }

    public Long getId() {
        return id;
    }

    public Long getFoodId() {
        return foodId;
    }

    public String getFoodName() {
        return foodName;
    }

    public BigDecimal getAmountG() {
        return amountG;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
