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
    FILE_REQUIRED("사진 파일이 필요합니다.", HttpStatus.BAD_REQUEST),
    CAPTION_TOO_LONG("한 줄은 %d자까지 쓸 수 있습니다.", HttpStatus.BAD_REQUEST),
    IMAGE_UNREADABLE("사진을 읽을 수 없습니다.", HttpStatus.BAD_REQUEST),
    IMAGE_TOO_MANY_PIXELS("사진의 가로·세로가 너무 큽니다.", HttpStatus.BAD_REQUEST),

    // Not Found(404)
    USER_NOT_FOUND("해당 사용자를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    // 방문자 사진 · 스팟 하트가 같이 쓴다 — 없는 poi 이거나 화면에 나오는 스팟이 아닐 때
    POI_NOT_FOUND("스팟을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    PHOTO_NOT_FOUND("사진을 찾을 수 없습니다.", HttpStatus.NOT_FOUND),
    PLACE_NOT_FOUND("맛집 · 숙소를 찾을 수 없습니다.", HttpStatus.NOT_FOUND),

    // etc 4xx
    INVALID_TOKEN("유효하지 않은 토큰입니다.", HttpStatus.UNAUTHORIZED),
    TOKEN_EXPIRED("만료된 토큰입니다.", HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED("해당 요청에 대한 권한이 없습니다.", HttpStatus.FORBIDDEN),
    UNSUPPORTED_IMAGE_TYPE("JPEG·PNG 사진만 올릴 수 있습니다.", HttpStatus.UNSUPPORTED_MEDIA_TYPE),

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
