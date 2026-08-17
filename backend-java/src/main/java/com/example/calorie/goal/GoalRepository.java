package com.example.calorie.goal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface GoalRepository extends JpaRepository<Goal, Long> {

    Optional<Goal> findByUserIdAndStartOn(Long userId, LocalDate startOn);

    /** 指定日時点で有効な目標。start_on が指定日以前で最も新しいもの。 */
    Optional<Goal> findFirstByUserIdAndStartOnLessThanEqualOrderByStartOnDesc(
            Long userId, LocalDate date);

    /**
     * 期間の日別集計で「その日に有効だった目標」を引くための一覧。
     *
     * <p>期間の開始日より前の目標も 1 件必要になるため、上限だけで絞って
     * 降順に取得し、呼び出し側で日付ごとに割り当てる。日ごとにクエリを
     * 発行すると、30 日分のグラフで 30 回引くことになるため。
     */
    List<Goal> findByUserIdAndStartOnLessThanEqualOrderByStartOnDesc(Long userId, LocalDate to);
}
