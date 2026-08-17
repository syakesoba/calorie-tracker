package com.example.calorie.profile;

import java.math.BigDecimal;

/**
 * 活動レベル。TDEE を求めるときに BMR に掛ける係数を持つ。
 */
public enum ActivityLevel {

    /** ほぼ座位。 */
    SEDENTARY("1.2"),

    /** 軽い運動（週 1〜3 回）。 */
    LIGHT("1.375"),

    /** 中程度の運動（週 3〜5 回）。 */
    MODERATE("1.55"),

    /** 激しい運動（週 6〜7 回）。 */
    ACTIVE("1.725"),

    /** 非常に激しい運動、または肉体労働。 */
    VERY_ACTIVE("1.9");

    private final BigDecimal factor;

    ActivityLevel(String factor) {
        this.factor = new BigDecimal(factor);
    }

    public BigDecimal factor() {
        return factor;
    }
}
