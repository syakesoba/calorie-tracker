package com.example.calorie.common;

import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

/**
 * 例外を {@link ApiErrorResponse} に変換する。
 *
 * <p>想定外の例外の詳細をクライアントへ返さないことを、ここで一元的に担保する。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(ApiException e) {
        return ResponseEntity
                .status(e.getStatus())
                .body(ApiErrorResponse.of(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        List<ApiErrorResponse.FieldErrorDetail> details = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiErrorResponse.FieldErrorDetail(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return ResponseEntity
                .badRequest()
                .body(ApiErrorResponse.validation("入力内容に誤りがあります。", details));
    }

    /**
     * {@code @RequestParam} や {@code @PathVariable} に付けた制約の違反。
     * リクエストボディの検証（上）とは別の例外型になるが、応答形式は揃える。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(ConstraintViolationException e) {
        List<ApiErrorResponse.FieldErrorDetail> details = e.getConstraintViolations().stream()
                .map(v -> new ApiErrorResponse.FieldErrorDetail(lastNode(v), v.getMessage()))
                .toList();
        return ResponseEntity
                .badRequest()
                .body(ApiErrorResponse.validation("入力内容に誤りがあります。", details));
    }

    /** "search.query" のようなプロパティパスから、末尾のパラメータ名だけを取り出す。 */
    private static String lastNode(jakarta.validation.ConstraintViolation<?> violation) {
        String path = violation.getPropertyPath().toString();
        int lastDot = path.lastIndexOf('.');
        return lastDot < 0 ? path : path.substring(lastDot + 1);
    }

    /** 型が合わないクエリパラメータ（日付形式の誤りなど）。 */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiErrorResponse> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        return ResponseEntity
                .badRequest()
                .body(ApiErrorResponse.validation("入力内容に誤りがあります。", List.of(
                        new ApiErrorResponse.FieldErrorDetail(e.getName(), "形式が正しくありません")
                )));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception e) {
        // 原因はサーバーログにのみ残し、クライアントへは汎用メッセージだけを返す
        log.error("想定外のエラーが発生しました", e);
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiErrorResponse.of("INTERNAL_ERROR", "サーバー内部でエラーが発生しました。"));
    }
}
