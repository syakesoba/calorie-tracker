package com.example.calorie.common

import java.time.LocalDate

/**
 * 期間指定の検証。体重推移と期間集計で同じ規則を使うため、ここに集約する。
 */
object DateRanges {

    /**
     * 受け付ける期間の上限。
     *
     * 上限を設けているのは、無制限の期間を投げられると 1 リクエストで
     * 何年分もの行を読むことになるため。グラフ用途では 2 年で十分足りる。
     */
    private const val MAX_YEARS = 2L

    fun requireValid(from: LocalDate, to: LocalDate) {
        if (from.isAfter(to)) {
            throw ApiException.badRequest("INVALID_RANGE", "開始日は終了日以前にしてください。")
        }
        if (from.plusYears(MAX_YEARS).isBefore(to)) {
            throw ApiException.badRequest("RANGE_TOO_LONG", "期間は $MAX_YEARS 年以内にしてください。")
        }
    }
}
