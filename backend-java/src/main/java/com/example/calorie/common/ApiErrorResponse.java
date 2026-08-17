package com.example.calorie.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * エラー応答の共通形式。
 *
 * <p>Kotlin 実装も必ずこの形で返すこと。クライアント（Next.js / Expo）は
 * 生成された 1 つの型でエラーを扱うため、形が揺れると両対応できなくなる。
 *
 * @param code    プログラムから分岐するための機械可読なコード
 * @param message 画面にそのまま出せる日本語メッセージ
 * @param errors  入力検証エラーの明細。検証エラー以外では null。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        String code,
        String message,
        List<FieldErrorDetail> errors
) {

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(code, message, null);
    }

    public static ApiErrorResponse validation(String message, List<FieldErrorDetail> errors) {
        return new ApiErrorResponse("VALIDATION_ERROR", message, errors);
    }

    /** どのフィールドがなぜ弾かれたか。 */
    public record FieldErrorDetail(String field, String message) {
    }
}
