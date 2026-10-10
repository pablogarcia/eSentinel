package bo.solid.fiscal.domain.shared;

public class FiscalException extends RuntimeException {

    public enum Kind { NOT_FOUND, CONFLICT, UNPROCESSABLE, UNAUTHENTICATED, UPSTREAM_UNAVAILABLE, UPSTREAM_TIMEOUT, UPSTREAM_REJECTED, SIAT_REJECTED }

    private final String code;
    private final ErrorCategory category;
    private final Kind kind;
    private final java.util.List<String> details;

    public FiscalException(Kind kind, ErrorCategory category, String code, String message) {
        this(kind, category, code, message, java.util.List.of());
    }

    public FiscalException(Kind kind, ErrorCategory category, String code, String message, java.util.List<String> details) {
        super(message);
        this.details = java.util.List.copyOf(details);
        this.kind = kind;
        this.category = category;
        this.code = code;
    }

    public static FiscalException notFound(String code, String message) {
        return new FiscalException(Kind.NOT_FOUND, ErrorCategory.BUSINESS, code, message);
    }

    public static FiscalException conflict(String code, String message) {
        return new FiscalException(Kind.CONFLICT, ErrorCategory.BUSINESS, code, message);
    }

    public static FiscalException unprocessable(String code, String message) {
        return new FiscalException(Kind.UNPROCESSABLE, ErrorCategory.VALIDATION, code, message);
    }

    public static FiscalException unauthenticated(String message) {
        return new FiscalException(Kind.UNAUTHENTICATED, ErrorCategory.SECURITY, "SECURITY_001", message);
    }

    /** El SIAT no responde (conexion, DNS, 5xx de infraestructura). Categoria NETWORK. */
    public static FiscalException siatUnavailable(String message) {
        return new FiscalException(Kind.UPSTREAM_UNAVAILABLE, ErrorCategory.NETWORK, "SIAT_NET_001", message);
    }

    /** Se agoto el tiempo esperando al SIAT: el resultado de una operacion de escritura es DESCONOCIDO. */
    public static FiscalException siatTimeout(String message) {
        return new FiscalException(Kind.UPSTREAM_TIMEOUT, ErrorCategory.NETWORK, "SIAT_NET_002", message);
    }

    /** El SIAT rechazo nuestra autenticacion (token delegado invalido/vencido). */
    public static FiscalException siatAuth(String message) {
        return new FiscalException(Kind.UPSTREAM_REJECTED, ErrorCategory.SIAT, "SIAT_AUTH_001", message);
    }

    /** Respuesta del SIAT inesperada o ilegible (falla de contrato). */
    public static FiscalException siatProtocol(String message) {
        return new FiscalException(Kind.UPSTREAM_REJECTED, ErrorCategory.SIAT, "SIAT_PROTO_001", message);
    }

    /** El SIAT proceso la solicitud y la rechazo (transaccion=false) con mensajes de servicio. */
    public static FiscalException siatRejected(String message, java.util.List<String> siatMessages) {
        return new FiscalException(Kind.SIAT_REJECTED, ErrorCategory.SIAT, "SIAT_REJ_001", message, siatMessages);
    }

    public java.util.List<String> details() { return details; }

    public String code() { return code; }
    public ErrorCategory category() { return category; }
    public Kind kind() { return kind; }
}
