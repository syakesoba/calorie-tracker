package com.example.calorie.body;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 体重・体脂肪率の記録。1 ユーザー 1 日 1 レコード（DB のユニーク制約で担保）。
 */
@Entity
@Table(name = "body_records")
public class BodyRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "recorded_on", nullable = false)
    private LocalDate recordedOn;

    @Column(name = "weight_kg", nullable = false, precision = 5, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "body_fat_pct", precision = 4, scale = 1)
    private BigDecimal bodyFatPct;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected BodyRecord() {
    }

    public static BodyRecord create(Long userId, LocalDate recordedOn,
                                    BigDecimal weightKg, BigDecimal bodyFatPct) {
        BodyRecord record = new BodyRecord();
        record.userId = userId;
        record.recordedOn = recordedOn;
        record.weightKg = weightKg;
        record.bodyFatPct = bodyFatPct;
        OffsetDateTime now = OffsetDateTime.now();
        record.createdAt = now;
        record.updatedAt = now;
        return record;
    }

    public void update(BigDecimal weightKg, BigDecimal bodyFatPct) {
        this.weightKg = weightKg;
        this.bodyFatPct = bodyFatPct;
        this.updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public LocalDate getRecordedOn() {
        return recordedOn;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public BigDecimal getBodyFatPct() {
        return bodyFatPct;
    }
}
