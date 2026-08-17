package com.example.calorie.meal;

import com.example.calorie.common.Nutrition;
import com.example.calorie.food.Food;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 1 回の食事の記録。「2026-08-17 の朝食」が 1 レコードで、その中に品目が並ぶ。
 */
@Entity
@Table(name = "meal_logs")
public class MealLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "eaten_on", nullable = false)
    private LocalDate eatenOn;

    @Enumerated(EnumType.STRING)
    @Column(name = "meal_type", nullable = false, length = 20)
    private MealType mealType;

    @Column(name = "note", length = 500)
    private String note;

    @OneToMany(mappedBy = "mealLog", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder asc")
    private List<MealLogItem> items = new ArrayList<>();

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected MealLog() {
    }

    public static MealLog create(Long userId, LocalDate eatenOn, MealType mealType, String note) {
        MealLog log = new MealLog();
        log.userId = userId;
        log.eatenOn = eatenOn;
        log.mealType = mealType;
        log.note = note;
        OffsetDateTime now = OffsetDateTime.now();
        log.createdAt = now;
        log.updatedAt = now;
        return log;
    }

    /**
     * 品目を追加する。栄養値はこの時点のマスタ値から換算して確定する。
     *
     * @param amountG 分量（グラム）
     */
    public void addItem(Food food, BigDecimal amountG) {
        items.add(MealLogItem.snapshot(this, food, amountG, items.size()));
    }

    /**
     * この食事の合計。品目のスナップショットを足し合わせるだけで、
     * 食品マスタは参照しない。
     */
    public Nutrition total() {
        return items.stream()
                .map(MealLogItem::nutrition)
                .reduce(Nutrition.ZERO, Nutrition::plus);
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDate getEatenOn() {
        return eatenOn;
    }

    public MealType getMealType() {
        return mealType;
    }

    public String getNote() {
        return note;
    }

    public List<MealLogItem> getItems() {
        return List.copyOf(items);
    }
}
