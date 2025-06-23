package com.flab.exception;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.dao.DataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.servlet.NoHandlerFoundException;

import java.sql.SQLException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private final ExceptionMetrics exceptionMetrics;

    public GlobalExceptionHandler(ExceptionMetrics exceptionMetrics) {
        this.exceptionMetrics = exceptionMetrics;
    }

    // 404 에러 처리
    @ExceptionHandler(NoHandlerFoundException.class)
    public ResponseEntity<String> handleNotFound(NoHandlerFoundException ex, HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("요청하신 경로를 찾을 수 없습니다: " + request.getRequestURI());
    }

    // DB 트랜잭션 오류
    @ExceptionHandler(CannotCreateTransactionException.class)
    public ResponseEntity<String> handleTransactionException(CannotCreateTransactionException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body("DB 연결이 불안정합니다. 잠시 후 다시 시도해주세요.");
    }

    // DB 접근 오류
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<String> handleDataAccessException(DataAccessException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("데이터 처리 중 오류가 발생했습니다.");
    }

    // SQL 오류
    @ExceptionHandler(SQLException.class)
    public ResponseEntity<String> handleSqlException(SQLException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("SQL 오류가 발생했습니다: " + ex.getMessage());
    }

    // 모든 예외 처리 (500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleAllExceptions(Exception ex) {
        exceptionMetrics.incrementHttp500();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body("Internal Server Error");
    }

}

