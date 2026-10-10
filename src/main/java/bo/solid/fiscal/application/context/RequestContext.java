package bo.solid.fiscal.application.context;

import java.util.Optional;
import java.util.Set;

/** Quien/desde donde se ejecuta la operacion actual (para auditoria). Se limpia en finally. */
public final class RequestContext {

    public record Info(String actorId, Set<String> roles, String ip, String correlationId, String traceId) {
        public static Info system() { return new Info("system", Set.of("SYSTEM"), null, null, null); }
    }

    private static final ThreadLocal<Info> CURRENT = new ThreadLocal<>();

    private RequestContext() {}

    public static void set(Info info) { CURRENT.set(info); }

    public static void clear() { CURRENT.remove(); }

    public static Optional<Info> current() { return Optional.ofNullable(CURRENT.get()); }

    public static Info currentOrSystem() { return current().orElseGet(Info::system); }
}
