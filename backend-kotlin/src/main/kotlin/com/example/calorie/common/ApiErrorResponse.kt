package com.example.calorie.common

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * エラー応答の共通形式。
 *
 * Java 実装と **完全に同じ形** で返すこと。クライアント（Next.js / Expo）は
 * 1 つの型でエラーを扱うため、形が揺れると両対応できなくなる。
 *
 * @param code プログラムから分岐するための機械可読なコード
 * @param message 画面にそのまま出せる日本語メッセージ
 * @param errors 入力検証エラーの明細。検証エラー以外では null（キーごと省略される）。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class ApiErrorResponse(
    val code: String,
    val message: String,
    val errors: List<FieldErrorDetail>? = null,
) {
    /** どのフィールドがなぜ弾かれたか。 */
    data class FieldErrorDetail(val field: String, val message: String)

    companion object {
        fun of(code: String, message: String) = ApiErrorResponse(code, message)

        fun validation(message: String, errors: List<FieldErrorDetail>) =
            ApiErrorResponse("VALIDATION_ERROR", message, errors)
    }
}
