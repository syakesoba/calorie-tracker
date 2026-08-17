package com.example.calorie.body.dto

import com.example.calorie.body.BodyRecord
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import java.math.BigDecimal
import java.time.LocalDate

data class BodyRecordRequest(
    @field:DecimalMin("20") @field:DecimalMax("300") val weightKg: BigDecimal,
    @field:DecimalMin("1") @field:DecimalMax("70") val bodyFatPct: BigDecimal? = null,
)

data class BodyRecordResponse(
    val recordedOn: LocalDate,
    val weightKg: BigDecimal,
    val bodyFatPct: BigDecimal?,
) {
    companion object {
        fun from(record: BodyRecord) =
            BodyRecordResponse(record.recordedOn, record.weightKg, record.bodyFatPct)
    }
}
