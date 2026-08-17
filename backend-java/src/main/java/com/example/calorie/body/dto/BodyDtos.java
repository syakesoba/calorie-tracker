package com.example.calorie.body.dto;

import com.example.calorie.body.BodyRecord;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

public final class BodyDtos {

    private BodyDtos() {
    }

    public record BodyRecordRequest(
            @NotNull @DecimalMin("20") @DecimalMax("300") BigDecimal weightKg,
            @DecimalMin("1") @DecimalMax("70") BigDecimal bodyFatPct
    ) {
    }

    public record BodyRecordResponse(
            LocalDate recordedOn,
            BigDecimal weightKg,
            BigDecimal bodyFatPct
    ) {
        public static BodyRecordResponse from(BodyRecord record) {
            return new BodyRecordResponse(
                    record.getRecordedOn(), record.getWeightKg(), record.getBodyFatPct());
        }
    }
}
