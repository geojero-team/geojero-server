package com.example.geojeroserver.exception;

import lombok.Getter;

@Getter
public class InfraException extends RuntimeException {

    private final ErrorCode errorCode;
    private final transient Object[] args;

    public InfraException(ErrorCode errorCode, Object... args) {
        super(errorCode.formatMessage(args));
        this.errorCode = errorCode;
        this.args = args;
    }

    public InfraException(ErrorCode errorCode, Throwable cause, Object... args) {
        super(errorCode.formatMessage(args), cause);
        this.errorCode = errorCode;
        this.args = args;
    }
}
