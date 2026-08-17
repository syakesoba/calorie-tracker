package com.example.calorie.body

import com.example.calorie.body.dto.BodyRecordRequest
import com.example.calorie.body.dto.BodyRecordResponse
import com.example.calorie.common.DateRanges
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class BodyRecordService(private val repository: BodyRecordRepository) {

    @Transactional(readOnly = true)
    fun list(userId: Long, from: LocalDate, to: LocalDate): List<BodyRecordResponse> {
        DateRanges.requireValid(from, to)
        return repository
            .findByUserIdAndRecordedOnBetweenOrderByRecordedOnAsc(userId, from, to)
            .map(BodyRecordResponse::from)
    }

    /** 1 日 1 レコード。同じ日に再度記録した場合は上書きする。 */
    @Transactional
    fun put(userId: Long, date: LocalDate, request: BodyRecordRequest): BodyRecordResponse {
        val existing = repository.findByUserIdAndRecordedOn(userId, date)

        val record = if (existing != null) {
            existing.update(request.weightKg, request.bodyFatPct)
            existing
        } else {
            repository.save(
                BodyRecord.create(userId, date, request.weightKg, request.bodyFatPct)
            )
        }

        return BodyRecordResponse.from(record)
    }

    /** 削除。元から無い場合もエラーにしない（冪等）。 */
    @Transactional
    fun delete(userId: Long, date: LocalDate) {
        repository.deleteByUserIdAndRecordedOn(userId, date)
    }
}
