package bo.solid.fiscal.interfaces.security;

import bo.solid.fiscal.application.context.RequestContext;
import bo.solid.fiscal.application.tenancy.TenantContext;
import bo.solid.fiscal.interfaces.web.CorrelationIdFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.slf4j.MDC;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Despues de validar el JWT: fija el tenant (claim tenant_id) y el contexto de auditoria. Un token de tenant
 * sin tenant_id valido no pasa; solo SUPER_ADMIN (plataforma) opera sin tenant. Limpia siempre al terminar.
 */
public class RequestContextFilter extends OncePerRequestFilter {

    private static final Pattern TRACEPARENT = Pattern.compile("^[0-9a-f]{2}-([0-9a-f]{32})-[0-9a-f]{16}-[0-9a-f]{2}$");

    private final String tenantClaim;
    private final ApiErrorWriter errors;

    public RequestContextFilter(String tenantClaim, ApiErrorWriter errors) {
        this.tenantClaim = tenantClaim;
        this.errors = errors;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth instanceof JwtAuthenticationToken jwt && auth.isAuthenticated()) {
                Set<String> roles = auth.getAuthorities().stream().map(GrantedAuthority::getAuthority)
                        .filter(a -> a.startsWith("ROLE_")).map(a -> a.substring(5)).collect(Collectors.toSet());
                String tenantRaw = jwt.getToken().getClaimAsString(tenantClaim);
                if (tenantRaw != null) {
                    try {
                        TenantContext.set(UUID.fromString(tenantRaw));
                    } catch (IllegalArgumentException e) {
                        errors.write(response, HttpServletResponse.SC_FORBIDDEN, "SECURITY_003", "tenant_id invalido en el token");
                        return;
                    }
                } else if (!roles.contains(Roles.SUPER_ADMIN)) {
                    errors.write(response, HttpServletResponse.SC_FORBIDDEN, "SECURITY_002", "El token no trae tenant_id");
                    return;
                }
                RequestContext.set(new RequestContext.Info(jwt.getName(), roles, request.getRemoteAddr(),
                        MDC.get(CorrelationIdFilter.MDC_KEY), traceId(request.getHeader("traceparent"))));
            }
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            RequestContext.clear();
        }
    }

    private static String traceId(String traceparent) {
        if (traceparent == null) return null;
        Matcher m = TRACEPARENT.matcher(traceparent.strip());
        return m.matches() ? m.group(1) : null;
    }
}
