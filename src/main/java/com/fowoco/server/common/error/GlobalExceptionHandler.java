package com.fowoco.server.common.error;

import com.fowoco.server.common.database.DatabaseAccessDeniedMetrics;
import com.fowoco.server.common.database.DatabaseTimeoutMetrics;
import com.fowoco.server.common.database.DatabaseTimeoutType;
import com.fowoco.server.common.database.PostgreSqlAccessDeniedClassification;
import com.fowoco.server.common.database.PostgreSqlAccessDeniedClassifier;
import com.fowoco.server.common.database.PostgreSqlTimeoutClassification;
import com.fowoco.server.common.database.PostgreSqlTimeoutClassifier;
import com.fowoco.server.common.web.RequestIdFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final Clock clock;
    private final PostgreSqlAccessDeniedClassifier accessDeniedClassifier;
    private final DatabaseAccessDeniedMetrics accessDeniedMetrics;
    private final PostgreSqlTimeoutClassifier timeoutClassifier;
    private final DatabaseTimeoutMetrics timeoutMetrics;

    public GlobalExceptionHandler(
            Clock clock,
            PostgreSqlAccessDeniedClassifier accessDeniedClassifier,
            DatabaseAccessDeniedMetrics accessDeniedMetrics,
            PostgreSqlTimeoutClassifier timeoutClassifier,
            DatabaseTimeoutMetrics timeoutMetrics
    ) {
        this.clock = clock;
        this.accessDeniedClassifier = accessDeniedClassifier;
        this.accessDeniedMetrics = accessDeniedMetrics;
        this.timeoutClassifier = timeoutClassifier;
        this.timeoutMetrics = timeoutMetrics;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiErrorResponse> handleApiException(
            ApiException exception,
            HttpServletRequest request
    ) {
        return response(exception.errorCode(), exception.getMessage(), List.of(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiErrorResponse> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<FieldErrorResponse> fieldErrors = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorResponse(error.getField(), error.getDefaultMessage()))
                .sorted(Comparator.comparing(FieldErrorResponse::field))
                .toList();
        return response(ErrorCode.VALIDATION_FAILED, null, fieldErrors, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<FieldErrorResponse> fieldErrors = exception.getConstraintViolations().stream()
                .map(violation -> new FieldErrorResponse(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                ))
                .sorted(Comparator.comparing(FieldErrorResponse::field))
                .toList();
        return response(ErrorCode.VALIDATION_FAILED, null, fieldErrors, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodValidation(
            HandlerMethodValidationException exception,
            HttpServletRequest request
    ) {
        return response(ErrorCode.VALIDATION_FAILED, null, List.of(), request);
    }

    @ExceptionHandler({
            HttpMessageNotReadableException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class
    })
    public ResponseEntity<ApiErrorResponse> handleInvalidRequest(
            Exception exception,
            HttpServletRequest request
    ) {
        return response(ErrorCode.INVALID_REQUEST, null, List.of(), request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthentication(
            AuthenticationException exception,
            HttpServletRequest request
    ) {
        return response(ErrorCode.AUTHENTICATION_REQUIRED, null, List.of(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiErrorResponse> handleAccessDenied(
            AccessDeniedException exception,
            HttpServletRequest request
    ) {
        return response(ErrorCode.ACCESS_DENIED, null, List.of(), request);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiErrorResponse> handleOptimisticLock(
            ObjectOptimisticLockingFailureException exception,
            HttpServletRequest request
    ) {
        return handleDatabaseAccessDenied(exception, request)
                .orElseGet(() -> response(
                        ErrorCode.CONCURRENT_MODIFICATION,
                        null,
                        List.of(),
                        request
                ));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
            HttpRequestMethodNotSupportedException exception,
            HttpServletRequest request
    ) {
        return response(ErrorCode.METHOD_NOT_ALLOWED, null, List.of(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiErrorResponse> handleNotAcceptable(
            HttpMediaTypeNotAcceptableException exception,
            HttpServletRequest request
    ) {
        return response(ErrorCode.NOT_ACCEPTABLE, null, List.of(), request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(
            HttpMediaTypeNotSupportedException exception,
            HttpServletRequest request
    ) {
        return response(ErrorCode.UNSUPPORTED_MEDIA_TYPE, null, List.of(), request);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleNotFound(
            NoResourceFoundException exception,
            HttpServletRequest request
    ) {
        return response(ErrorCode.RESOURCE_NOT_FOUND, null, List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(
            Exception exception,
            HttpServletRequest request
    ) {
        Optional<ResponseEntity<ApiErrorResponse>> accessDeniedResponse =
                handleDatabaseAccessDenied(exception, request);
        if (accessDeniedResponse.isPresent()) {
            return accessDeniedResponse.get();
        }

        PostgreSqlTimeoutClassification classification =
                timeoutClassifier.classify(exception);
        if (isConfirmed(classification.type())) {
            timeoutMetrics.recordConfirmed(classification.type());
            logDatabaseFailure(
                    request,
                    confirmedLogCode(classification.type()),
                    classification.sqlState(),
                    classification.exceptionType()
            );
            return response(
                    ErrorCode.SERVICE_TEMPORARILY_UNAVAILABLE,
                    null,
                    List.of(),
                    request,
                    safeRoute(request)
            );
        }
        if (isAmbiguous(classification.type())) {
            logDatabaseFailure(
                    request,
                    classification.type().name(),
                    classification.sqlState(),
                    classification.exceptionType()
            );
            return response(
                    ErrorCode.INTERNAL_SERVER_ERROR,
                    null,
                    List.of(),
                    request,
                    safeRoute(request)
            );
        }

        String requestId = requestId(request);
        log.error(
                "Unexpected server error: requestId={}, method={}, path={}, exceptionType={}",
                requestId,
                request.getMethod(),
                request.getRequestURI(),
                exception.getClass().getName(),
                exception
        );
        return response(ErrorCode.INTERNAL_SERVER_ERROR, null, List.of(), request);
    }

    private Optional<ResponseEntity<ApiErrorResponse>> handleDatabaseAccessDenied(
            Throwable failure,
            HttpServletRequest request
    ) {
        PostgreSqlAccessDeniedClassification classification =
                accessDeniedClassifier.classify(failure);
        if (!classification.confirmed()) {
            return Optional.empty();
        }

        accessDeniedMetrics.recordConfirmed();
        logDatabaseFailure(
                request,
                "DATABASE_ACCESS_DENIED",
                classification.sqlState(),
                classification.exceptionType()
        );
        return Optional.of(response(
                ErrorCode.INTERNAL_SERVER_ERROR,
                null,
                List.of(),
                request,
                safeRoute(request)
        ));
    }

    private void logDatabaseFailure(
            HttpServletRequest request,
            String logClassification,
            String sqlState,
            String exceptionType
    ) {
        log.warn(
                "Database request failure: requestId={} method={} route={} "
                        + "classification={} sqlState={} exceptionType={}",
                requestId(request),
                request.getMethod(),
                safeRoute(request),
                logClassification,
                sqlState,
                exceptionType
        );
    }

    private boolean isConfirmed(DatabaseTimeoutType type) {
        return type == DatabaseTimeoutType.CONFIRMED_STATEMENT_TIMEOUT
                || type == DatabaseTimeoutType.CONFIRMED_LOCK_TIMEOUT;
    }

    private boolean isAmbiguous(DatabaseTimeoutType type) {
        return type == DatabaseTimeoutType.AMBIGUOUS_QUERY_CANCELED
                || type == DatabaseTimeoutType.AMBIGUOUS_LOCK_NOT_AVAILABLE;
    }

    private String confirmedLogCode(DatabaseTimeoutType type) {
        return switch (type) {
            case CONFIRMED_STATEMENT_TIMEOUT -> "DATABASE_STATEMENT_TIMEOUT";
            case CONFIRMED_LOCK_TIMEOUT -> "DATABASE_LOCK_TIMEOUT";
            default -> throw new IllegalArgumentException("Confirmed timeout is required");
        };
    }

    private String safeRoute(HttpServletRequest request) {
        Object route = request.getAttribute(
                HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE
        );
        return route == null ? "unknown" : route.toString();
    }

    private ResponseEntity<ApiErrorResponse> response(
            ApiErrorCode errorCode,
            String message,
            List<FieldErrorResponse> fieldErrors,
            HttpServletRequest request
    ) {
        return response(
                errorCode,
                message,
                fieldErrors,
                request,
                request.getRequestURI()
        );
    }

    private ResponseEntity<ApiErrorResponse> response(
            ApiErrorCode errorCode,
            String message,
            List<FieldErrorResponse> fieldErrors,
            HttpServletRequest request,
            String safePath
    ) {
        ApiErrorResponse body = new ApiErrorResponse(
                Instant.now(clock),
                errorCode.status().value(),
                errorCode.code(),
                message == null || message.isBlank() ? errorCode.defaultMessage() : message,
                safePath,
                requestId(request),
                fieldErrors
        );
        return ResponseEntity.status(errorCode.status()).body(body);
    }

    private String requestId(HttpServletRequest request) {
        Object requestId = request.getAttribute(RequestIdFilter.ATTRIBUTE_NAME);
        return requestId == null ? "unknown" : requestId.toString();
    }
}
