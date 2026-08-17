package com.example.calorie.body

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface BodyRecordRepository : JpaRepository<BodyRecord, Long> {

    fun findByUserIdAndRecordedOn(userId: Long, recordedOn: LocalDate): BodyRecord?

    fun findByUserIdAndRecordedOnBetweenOrderByRecordedOnAsc(
        userId: Long,
        from: LocalDate,
        to: LocalDate,
    ): List<BodyRecord>

    /**
     * 指定日以前で最も新しい記録。目標算出に使う「最新の体重」を引くためのもの。
     * 今日の記録がまだ無くても、直近の記録で計算できるようにする。
     */
    fun findFirstByUserIdAndRecordedOnLessThanEqualOrderByRecordedOnDesc(
        userId: Long,
        date: LocalDate,
    ): BodyRecord?

    fun deleteByUserIdAndRecordedOn(userId: Long, recordedOn: LocalDate)
}
