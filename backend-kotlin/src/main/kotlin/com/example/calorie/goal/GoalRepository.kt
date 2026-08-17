package com.example.calorie.goal

import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface GoalRepository : JpaRepository<Goal, Long> {

    fun findByUserIdAndStartOn(userId: Long, startOn: LocalDate): Goal?

    /** 指定日時点で有効な目標。start_on が指定日以前で最も新しいもの。 */
    fun findFirstByUserIdAndStartOnLessThanEqualOrderByStartOnDesc(
        userId: Long,
        date: LocalDate,
    ): Goal?

    /**
     * 期間の日別集計で「その日に有効だった目標」を引くための一覧。
     *
     * 期間の開始日より前の目標も 1 件必要になるため、上限だけで絞って降順に取得し、
     * 呼び出し側で日付ごとに割り当てる。日ごとにクエリを発行すると、
     * 30 日分のグラフで 30 回引くことになるため。
     */
    fun findByUserIdAndStartOnLessThanEqualOrderByStartOnDesc(
        userId: Long,
        to: LocalDate,
    ): List<Goal>
}
