package bo.solid.fiscal.domain.shared;

public class FiscalException extends RuntimeException {

    public enum Kind { NOT_FOUND, CONFLICT, UNPROCESSABLE, UNAUTHENTICATED }

    private final String code;
    private final ErrorCategory category;
    private final Kind kind;

    public FiscalException(Kind kind, ErrorCategory category, String code, String message) {
        super(message);
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

    public String code() { return code; }
    public ErrorCategory category() { return category; }
    public Kind kind() { return kind; }
}
