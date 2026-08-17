package com.example.calorie.goal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 目標カロリーと目標 PFC。
 *
 * <p>履歴として複数行を持ち、{@code start_on} で「その日に有効な目標」を引く。
 * 上書きせず履歴にするのは、過去の達成率を後から計算し直せるようにするため。
 * 目標を変えた瞬間に過去の評価まで変わってしまうのは正しくない。
 */
@Entity
@Table(name = "goals")
public class Goal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "start_on", nullable = false)
    private LocalDate startOn;

    @Column(name = "target_kcal", nullable = false)
    private int targetKcal;

    @Column(name = "target_protein_g", nullable = false, precision = 6, scale = 1)
    private BigDecimal targetProteinG;

    @Column(name = "target_fat_g", nullable = false, precision = 6, scale = 1)
    private BigDecimal targetFatG;

    @Column(name = "target_carb_g", nullable = false, precision = 6, scale = 1)
    private BigDecimal targetCarbG;

    @Column(name = "pace_kg_per_month", precision = 4, scale = 2)
    private BigDecimal paceKgPerMonth;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Goal() {
    }

    public static Goal create(Long userId, LocalDate startOn, int targetKcal,
                              BigDecimal proteinG, BigDecimal fatG, BigDecimal carbG,
                              BigDecimal paceKgPerMonth) {
        Goal goal = new Goal();
        goal.userId = userId;
        goal.startOn = startOn;
        goal.createdAt = OffsetDateTime.now();
        goal.update(targetKcal, proteinG, fatG, carbG, paceKgPerMonth);
        return goal;
    }

    /** 同じ開始日の目標を上書きする場合に使う。 */
    public void update(int targetKcal, BigDecimal proteinG, BigDecimal fatG,
                       BigDecimal carbG, BigDecimal paceKgPerMonth) {
        this.targetKcal = targetKcal;
        this.targetProteinG = proteinG;
        this.targetFatG = fatG;
        this.targetCarbG = carbG;
        this.paceKgPerMonth = paceKgPerMonth;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public LocalDate getStartOn() {
        return startOn;
    }

    public int getTargetKcal() {
        return targetKcal;
    }

    public BigDecimal getTargetProteinG() {
        return targetProteinG;
    }

    public BigDecimal getTargetFatG() {
        return targetFatG;
    }

    public BigDecimal getTargetCarbG() {
        return targetCarbG;
    }

    public BigDecimal getPaceKgPerMonth() {
        return paceKgPerMonth;
    }
}
