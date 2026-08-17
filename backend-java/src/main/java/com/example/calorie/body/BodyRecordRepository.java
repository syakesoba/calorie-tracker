package com.example.calorie.body;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface BodyRecordRepository extends JpaRepository<BodyRecord, Long> {

    Optional<BodyRecord> findByUserIdAndRecordedOn(Long userId, LocalDate recordedOn);

    List<BodyRecord> findByUserIdAndRecordedOnBetweenOrderByRecordedOnAsc(
            Long userId, LocalDate from, LocalDate to);

    /**
     * 指定日以前で最も新しい記録。目標算出に使う「最新の体重」を引くためのもの。
     * 今日の記録がまだ無くても、直近の記録で計算できるようにする。
     */
    Optional<BodyRecord> findFirstByUserIdAndRecordedOnLessThanEqualOrderByRecordedOnDesc(
            Long userId, LocalDate date);

    void deleteByUserIdAndRecordedOn(Long userId, LocalDate recordedOn);
}
