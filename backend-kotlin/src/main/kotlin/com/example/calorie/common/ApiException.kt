package com.example.calorie.common

import org.springframework.http.HttpStatus

/**
 * クライアントへそのまま返してよい業務エラー。
 *
 * スタックトレースを持たせないのは、業務エラーは想定内の分岐であり
 * 例外生成コストを払う意味がないため。
 */
class ApiException(
    val status: HttpStatus,
    val code: String,
    override val message: String,
) : RuntimeException(message, null, false, false) {

    companion object {
        fun badRequest(code: String, message: String) =
            ApiException(HttpStatus.BAD_REQUEST, code, message)

        fun unauthorized(code: String, message: String) =
            ApiException(HttpStatus.UNAUTHORIZED, code, message)

        fun conflict(code: String, message: String) =
            ApiException(HttpStatus.CONFLICT, code, message)

        fun notFound(code: String, message: String) =
            ApiException(HttpStatus.NOT_FOUND, code, message)
    }
}
