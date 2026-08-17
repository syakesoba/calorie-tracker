package com.example.calorie.common

import jakarta.validation.ConstraintViolation
import jakarta.validation.ConstraintViolationException
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException

/**
 * 例外を [ApiErrorResponse] に変換する。
 *
 * 想定外の例外の詳細をクライアントへ返さないことを、ここで一元的に担保する。
 */
@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(ApiException::class)
    fun handleApiException(e: ApiException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.status(e.status).body(ApiErrorResponse.of(e.code, e.message))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidation(e: MethodArgumentNotValidException): ResponseEntity<ApiErrorResponse> {
        val details = e.bindingResult.fieldErrors.map {
            ApiErrorResponse.FieldErrorDetail(it.field, it.defaultMessage ?: "不正な値です")
        }
        return ResponseEntity.badRequest()
            .body(ApiErrorResponse.validation("入力内容に誤りがあります。", details))
    }

    /**
     * `@RequestParam` や `@PathVariable` に付けた制約の違反。
     * リクエストボディの検証とは別の例外型になるが、応答形式は揃える。
     */
    @ExceptionHandler(ConstraintViolationException::class)
    fun handleConstraintViolation(e: ConstraintViolationException): ResponseEntity<ApiErrorResponse> {
        val details = e.constraintViolations.map {
            ApiErrorResponse.FieldErrorDetail(it.lastNode(), it.message)
        }
        return ResponseEntity.badRequest()
            .body(ApiErrorResponse.validation("入力内容に誤りがあります。", details))
    }

    /** "search.query" のようなプロパティパスから、末尾のパラメータ名だけを取り出す。 */
    private fun ConstraintViolation<*>.lastNode(): String =
        propertyPath.toString().substringAfterLast('.')

    /** 型が合わないクエリパラメータ（日付形式の誤りなど）。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException::class)
    fun handleTypeMismatch(e: MethodArgumentTypeMismatchException): ResponseEntity<ApiErrorResponse> =
        ResponseEntity.badRequest().body(
            ApiErrorResponse.validation(
                "入力内容に誤りがあります。",
                listOf(ApiErrorResponse.FieldErrorDetail(e.name, "形式が正しくありません")),
            )
        )

    @ExceptionHandler(Exception::class)
    fun handleUnexpected(e: Exception): ResponseEntity<ApiErrorResponse> {
        // 原因はサーバーログにのみ残し、クライアントへは汎用メッセージだけを返す
        log.error("想定外のエラーが発生しました", e)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .body(ApiErrorResponse.of("INTERNAL_ERROR", "サーバー内部でエラーが発生しました。"))
    }
}
