package com.example.calorie.food;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FoodRepository extends JpaRepository<Food, Long> {

    /**
     * 食品名の部分一致検索。
     *
     * <p>共有マスタ（SEED / MEXT / OFF）に加えて、検索者自身が登録した USER food も
     * 対象に含む。<strong>他人が登録した USER food は決して返さない。</strong>
     *
     * <p>並び順は、共有マスタを先、ユーザー登録を後にする……のではなく逆にしている。
     * 自分で登録した食品は「マスタに無かったから登録した」ものであり、
     * 再び検索するときも第一候補であることが多いため。
     */
    @Query("""
            select f from Food f
            where f.name like %:query%
              and (f.source <> com.example.calorie.food.FoodSource.USER
                   or f.createdByUserId = :userId)
            order by
              case when f.createdByUserId = :userId then 0 else 1 end,
              length(f.name),
              f.name
            """)
    List<Food> search(@Param("query") String query, @Param("userId") Long userId, Limit limit);

    /**
     * 参照可能な食品を 1 件引く。他人の USER food は存在しないものとして扱う。
     * 記録を作るときに、他人の登録した食品を掴めないようにするための制約。
     */
    @Query("""
            select f from Food f
            where f.id = :id
              and (f.source <> com.example.calorie.food.FoodSource.USER
                   or f.createdByUserId = :userId)
            """)
    Optional<Food> findAccessibleById(@Param("id") Long id, @Param("userId") Long userId);

    /**
     * 参照可能な食品をまとめて引く。1 回の記録に複数の食品が含まれるため、
     * 件数分クエリを発行しないようにする。
     */
    @Query("""
            select f from Food f
            where f.id in :ids
              and (f.source <> com.example.calorie.food.FoodSource.USER
                   or f.createdByUserId = :userId)
            """)
    List<Food> findAllAccessibleByIds(@Param("ids") List<Long> ids, @Param("userId") Long userId);
}
