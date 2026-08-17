package com.example.calorie.food;

import com.example.calorie.common.Nutrition;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 食品マスタ。栄養値はすべて可食部 100g あたりで保持する。
 *
 * <p>分量に応じた換算はここでは行わず、記録を作る時点で {@link Nutrition#forAmountGrams}
 * を通してスナップショットを作る。
 */
@Entity
@Table(name = "foods")
public class Food {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, length = 10)
    private FoodSource source;

    @Column(name = "source_code", length = 64)
    private String sourceCode;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "name_kana", length = 200)
    private String nameKana;

    @Column(name = "category", length = 100)
    private String category;

    @Column(name = "kcal", nullable = false, precision = 7, scale = 2)
    private BigDecimal kcal;

    @Column(name = "protein_g", nullable = false, precision = 7, scale = 2)
    private BigDecimal proteinG;

    @Column(name = "fat_g", nullable = false, precision = 7, scale = 2)
    private BigDecimal fatG;

    @Column(name = "carb_g", nullable = false, precision = 7, scale = 2)
    private BigDecimal carbG;

    @Column(name = "salt_g", precision = 7, scale = 3)
    private BigDecimal saltG;

    @Column(name = "fiber_g", precision = 7, scale = 2)
    private BigDecimal fiberG;

    @Column(name = "sugar_g", precision = 7, scale = 2)
    private BigDecimal sugarG;

    /** USER 登録の場合の作成者。共有マスタ由来の食品は null。 */
    @Column(name = "created_by_user_id")
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Food() {
    }

    /**
     * ユーザーが独自に登録する食品を生成する。
     * 共有マスタ（SEED / MEXT / OFF）は Flyway と取り込みバッチが作るため、
     * アプリケーションから生成できるのは USER のみ。
     */
    public static Food createByUser(Long userId, String name, String nameKana,
                                    String category, Nutrition per100g) {
        Food food = new Food();
        food.source = FoodSource.USER;
        food.createdByUserId = userId;
        food.name = name;
        food.nameKana = nameKana;
        food.category = category;
        food.kcal = per100g.kcal();
        food.proteinG = per100g.proteinG();
        food.fatG = per100g.fatG();
        food.carbG = per100g.carbG();
        food.saltG = per100g.saltG();
        food.fiberG = per100g.fiberG();
        food.sugarG = per100g.sugarG();
        OffsetDateTime now = OffsetDateTime.now();
        food.createdAt = now;
        food.updatedAt = now;
        return food;
    }

    /** 可食部 100g あたりの栄養値。 */
    public Nutrition nutritionPer100g() {
        return new Nutrition(kcal, proteinG, fatG, carbG, saltG, fiberG, sugarG);
    }

    public Long getId() {
        return id;
    }

    public FoodSource getSource() {
        return source;
    }

    public String getSourceCode() {
        return sourceCode;
    }

    public String getName() {
        return name;
    }

    public String getNameKana() {
        return nameKana;
    }

    public String getCategory() {
        return category;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }
}
