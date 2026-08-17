package com.example.calorie.food;

/**
 * 食品データの出所。
 *
 * <p>出所を区別しておくことで、後から「どの値が検証済みか」を判別できる。
 * 名前を偽ってはならない。
 */
public enum FoodSource {

    /** Phase 1 で投入した代表値。おおよその値であり、正確な栄養計算には使えない。 */
    SEED,

    /** 日本食品標準成分表から取り込んだ値（Phase 3）。 */
    MEXT,

    /** Open Food Facts 由来（Phase 3）。 */
    OFF,

    /** ユーザーが手動登録した食品。登録者本人にのみ見える。 */
    USER
}
