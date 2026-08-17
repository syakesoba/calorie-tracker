package com.example.calorie.meal

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDate

interface MealLogRepository : JpaRepository<MealLog, Long> {

    /**
     * 指定日の食事記録を品目ごと取得する。
     *
     * `join fetch` で品目を同時に読むのは、記録が 4 件あると品目の取得で
     * 4 回追加クエリが飛ぶため（N+1）。
     */
    @Query(
        """
        select distinct l from MealLog l
        left join fetch l.mutableItems
        where l.userId = :userId and l.eatenOn = :date
        order by l.mealType, l.id
        """
    )
    fun findByUserIdAndDate(
        @Param("userId") userId: Long,
        @Param("date") date: LocalDate,
    ): List<MealLog>

    @Query(
        """
        select distinct l from MealLog l
        left join fetch l.mutableItems
        where l.userId = :userId and l.eatenOn between :from and :to
        order by l.eatenOn, l.mealType, l.id
        """
    )
    fun findByUserIdAndDateRange(
        @Param("userId") userId: Long,
        @Param("from") from: LocalDate,
        @Param("to") to: LocalDate,
    ): List<MealLog>

    /**
     * ID とユーザーの両方で絞る。
     *
     * ID だけで引いてから所有者を確認するのではなく、**クエリの条件に user_id を含める。**
     * 確認を書き忘れる余地をなくすため。他人の記録は「存在しない」ものとして扱われ、
     * 呼び出し側は 404 を返す。
     */
    fun findByIdAndUserId(id: Long, userId: Long): MealLog?
}
