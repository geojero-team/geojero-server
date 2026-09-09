package com.example.geojeroserver.exception;

import java.util.IllegalFormatException;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

import lombok.Getter;

@Getter
public enum ErrorCode {
    // 4xx Business Errors
    // Bad Request(400)
    VALIDATION_FAILED("입력값에 대한 유효성 검사에 실패했습니다.", HttpStatus.BAD_REQUEST),

    // Not Found(404)
    USER_NOT_FOUND("해당 사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    // etc 4xx
    INVALID_TOKEN("유효하지 않은 토큰입니다.", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED("만료된 토큰입니다.", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("해당 요청에 대한 권한이 없습니다.", HttpStatus.FORBIDDEN),

    //5xx System Errors
    INTERNAL_SERVER_ERROR("서버 내부에 에러가 발생하였습니다.", HttpStatus.INTERNAL_SERVER_ERROR);

    public static final String CODE_PROPERTY = "code";

    private final String message;
    private final HttpStatus status;

    ErrorCode(String message, HttpStatus status) {
        this.message = message;
        this.status = status;
    }
    public String getCode() {
        return name();
    }

    public String formatMessage(Object... args) {
        if (args == null || args.length == 0) {
            return this.message;
        }
        try {
            return String.format(this.message, args);
        } catch (IllegalFormatException e) {
            return this.message;
        }
    }

    public ProblemDetail toProblemDetail() {
        return toProblemDetail(this.message);
    }

    public ProblemDetail toProblemDetail(String detail) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(this.status, detail);
        problemDetail.setProperty(CODE_PROPERTY, getCode());
        return problemDetail;
    }
}
