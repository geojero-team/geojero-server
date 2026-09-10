package com.example.geojeroserver.exception;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.example.geojeroserver.logging.TraceIdFilter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestControllerAdvice
public class ExceptionHandlerAdvice extends ResponseEntityExceptionHandler {

    private static final String VALIDATION_ERRORS_PROPERTY = "validationErrors";

    @ExceptionHandler(BusinessException.class)
    public ProblemDetail handleBusinessException(BusinessException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();

        log.warn("BusinessException URI : {}, Code : {}, Message : {}, Args : {}, Origin : {}",
                request.getRequestURI(),
                errorCode.getCode(),
                ex.getMessage(),
                ex.getArgs(),
                origin(ex));

        return decorate(errorCode.toProblemDetail(ex.getMessage()), request.getRequestURI());
    }

    @ExceptionHandler(InfraException.class)
    public ProblemDetail handleInfraException(InfraException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();

        log.error("InfraException URI : {}, Code : {}, Message : {}, Args : {}",
                request.getRequestURI(),
                errorCode.getCode(),
                ex.getMessage(),
                ex.getArgs(),
                ex);

        return decorate(errorCode.toProblemDetail(ex.getMessage()), request.getRequestURI());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolationException(
            ConstraintViolationException ex, HttpServletRequest request) {

        List<ValidationError> validationErrors = ex.getConstraintViolations().stream()
                .map(violation -> new ValidationError(
                        violation.getPropertyPath().toString(),
                        stringify(violation.getInvalidValue()),
                        violation.getMessage()))
                .toList();

        ErrorCode errorCode = ErrorCode.VALIDATION_FAILED;
        ProblemDetail problemDetail = validationProblemDetail(validationErrors);
        decorate(problemDetail, request.getRequestURI());

        log.warn("Validation Fail URI : {}, Code : {}, Errors : {}",
                request.getRequestURI(), errorCode.getCode(), validationErrors);

        return problemDetail;
    }

    @ExceptionHandler(BindException.class)
    public ProblemDetail handleBindException(BindException ex, HttpServletRequest request) {
        List<ValidationError> validationErrors = toValidationErrors(ex.getFieldErrors());

        ProblemDetail problemDetail = validationProblemDetail(validationErrors);
        decorate(problemDetail, request.getRequestURI());

        log.warn("Validation Fail URI : {}, Code : {}, Errors : {}",
                request.getRequestURI(), ErrorCode.VALIDATION_FAILED.getCode(), validationErrors);

        return problemDetail;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUncaughtException(Exception ex, HttpServletRequest request) {
        ErrorCode errorCode = ErrorCode.INTERNAL_SERVER_ERROR;

        log.error("Unpredicted Error URI : {}, Method : {}, Type : {}",
                request.getRequestURI(),
                request.getMethod(),
                ex.getClass().getName(),
                ex);

        return decorate(errorCode.toProblemDetail(), request.getRequestURI());
    }


    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        List<ValidationError> validationErrors = toValidationErrors(ex.getBindingResult().getFieldErrors());

        return handleExceptionInternal(ex, validationProblemDetail(validationErrors), headers, status, request);
    }

    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

        List<ValidationError> validationErrors = ex.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream()
                        .map(error -> {
                            if (error instanceof FieldError fieldError) {
                                return new ValidationError(
                                        fieldError.getField(),
                                        stringify(fieldError.getRejectedValue()),
                                        fieldError.getDefaultMessage());
                            }
                            return new ValidationError(
                                    result.getMethodParameter().getParameterName(),
                                    stringify(result.getArgument()),
                                    error.getDefaultMessage());
                        }))
                .toList();

        return handleExceptionInternal(ex, validationProblemDetail(validationErrors), headers, status, request);
    }


    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex, @Nullable Object body, HttpHeaders headers, HttpStatusCode statusCode, WebRequest request) {

        ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
        if (response == null) {
            return null;
        }

        String uri = requestUri(request);
        if (response.getBody() instanceof ProblemDetail problemDetail) {
            fillCodeIfAbsent(problemDetail, statusCode);
            decorate(problemDetail, uri);
        }

        if (statusCode.is5xxServerError()) {
            log.error("MvcException URI : {}, Status : {}, Type : {}, Body : {}",
                    uri, statusCode.value(), ex.getClass().getSimpleName(), response.getBody(), ex);
        } else {
            log.warn("MvcException URI : {}, Status : {}, Type : {}, Body : {}",
                    uri, statusCode.value(), ex.getClass().getSimpleName(), response.getBody());
        }

        return response;
    }

    private List<ValidationError> toValidationErrors(List<FieldError> fieldErrors) {
        return fieldErrors.stream()
                .map(error -> new ValidationError(
                        error.getField(),
                        stringify(error.getRejectedValue()),
                        error.getDefaultMessage()))
                .toList();
    }

    private ProblemDetail validationProblemDetail(List<ValidationError> validationErrors) {
        ProblemDetail problemDetail = ErrorCode.VALIDATION_FAILED.toProblemDetail();
        problemDetail.setProperty(VALIDATION_ERRORS_PROPERTY, validationErrors);
        return problemDetail;
    }

    private void fillCodeIfAbsent(ProblemDetail problemDetail, HttpStatusCode statusCode) {
        Map<String, Object> properties = problemDetail.getProperties();
        if (properties != null && properties.containsKey(ErrorCode.CODE_PROPERTY)) {
            return;
        }
        HttpStatus resolved = HttpStatus.resolve(statusCode.value());
        problemDetail.setProperty(ErrorCode.CODE_PROPERTY,
                resolved != null ? resolved.name() : String.valueOf(statusCode.value()));
    }

    /** 모든 에러 응답에 요청 경로(instance)와 로그 추적용 traceId를 붙인다. */
    private ProblemDetail decorate(ProblemDetail problemDetail, @Nullable String uri) {
        if (uri != null) {
            problemDetail.setInstance(URI.create(uri));
        }
        String traceId = TraceIdFilter.currentTraceId();
        if (traceId != null) {
            problemDetail.setProperty(TraceIdFilter.TRACE_ID, traceId);
        }
        return problemDetail;
    }

    @Nullable
    private String requestUri(WebRequest request) {
        return (request instanceof ServletWebRequest servletWebRequest)
                ? servletWebRequest.getRequest().getRequestURI()
                : null;
    }

    private String origin(Throwable ex) {
        StackTraceElement[] stackTrace = ex.getStackTrace();
        return stackTrace.length > 0 ? stackTrace[0].toString() : "unknown";
    }

    private String stringify(@Nullable Object value) {
        return value == null ? "" : value.toString();
    }
}
