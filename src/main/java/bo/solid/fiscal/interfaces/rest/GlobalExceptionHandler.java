package bo.solid.fiscal.interfaces.rest;

import bo.solid.fiscal.domain.shared.ErrorCategory;
import bo.solid.fiscal.domain.shared.FiscalException;
import bo.solid.fiscal.interfaces.web.CorrelationIdFilter;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(FiscalException.class)
    ResponseEntity<ApiError> fiscal(FiscalException e) {
        HttpStatus status = switch (e.kind()) {
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case UNPROCESSABLE -> HttpStatus.UNPROCESSABLE_ENTITY;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case UPSTREAM_UNAVAILABLE -> HttpStatus.SERVICE_UNAVAILABLE;
            case UPSTREAM_TIMEOUT -> HttpStatus.GATEWAY_TIMEOUT;
            case UPSTREAM_REJECTED -> HttpStatus.BAD_GATEWAY;
            case SIAT_REJECTED -> HttpStatus.UNPROCESSABLE_ENTITY;
        };
        return build(status, e.code(), e.category(), e.getMessage(), e.details());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e) {
        List<String> details = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).toList();
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_001", ErrorCategory.VALIDATION,
                "Solicitud invalida", details);
    }

    @ExceptionHandler({IllegalArgumentException.class, HttpMessageNotReadableException.class})
    ResponseEntity<ApiError> badInput(Exception e) {
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_002", ErrorCategory.VALIDATION,
                e instanceof IllegalArgumentException ? e.getMessage() : "Cuerpo de la solicitud ilegible", List.of());
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> denied(AccessDeniedException e) {
        return build(HttpStatus.FORBIDDEN, "SECURITY_004", ErrorCategory.SECURITY, "Acceso denegado", List.of());
    }

    @ExceptionHandler(DataAccessException.class)
    ResponseEntity<ApiError> data(DataAccessException e) {
        log.error("Error de acceso a datos", e);
        return build(HttpStatus.SERVICE_UNAVAILABLE, "INFRA_001", ErrorCategory.INFRASTRUCTURE,
                "Error de infraestructura de datos", List.of());
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("Error no controlado", e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INFRA_000", ErrorCategory.INFRASTRUCTURE,
                "Error interno", List.of());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String code, ErrorCategory category, String message,
                                           List<String> details) {
        return ResponseEntity.status(status).body(new ApiError(code, category.name(), message, details, null,
                MDC.get(CorrelationIdFilter.MDC_KEY), Instant.now()));
    }
}
