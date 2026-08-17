package com.example.calorie.meal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface MealLogRepository extends JpaRepository<MealLog, Long> {

    /**
     * 指定日の食事記録を品目ごと取得する。
     *
     * <p>{@code join fetch} で品目を同時に読むのは、記録が 4 件あると
     * 品目の取得で 4 回追加クエリが飛ぶため（N+1）。
     */
    @Query("""
            select distinct l from MealLog l
            left join fetch l.items
            where l.userId = :userId and l.eatenOn = :date
            order by l.mealType, l.id
            """)
    List<MealLog> findByUserIdAndDate(@Param("userId") Long userId, @Param("date") LocalDate date);

    @Query("""
            select distinct l from MealLog l
            left join fetch l.items
            where l.userId = :userId and l.eatenOn between :from and :to
            order by l.eatenOn, l.mealType, l.id
            """)
    List<MealLog> findByUserIdAndDateRange(@Param("userId") Long userId,
                                           @Param("from") LocalDate from,
                                           @Param("to") LocalDate to);

    /**
     * ID とユーザーの両方で絞る。
     *
     * <p>ID だけで引いてから所有者を確認するのではなく、<strong>クエリの条件に
     * user_id を含める。</strong>確認を書き忘れる余地をなくすため。
     * 他人の記録は「存在しない」ものとして扱われ、呼び出し側は 404 を返す。
     */
    Optional<MealLog> findByIdAndUserId(Long id, Long userId);
}
