package com.example.geojeroserver.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@AllArgsConstructor
public class ValidationError {

    private final String field;
    private final String value;
    private final String reason;
}
