package bo.solid.fiscal.integration;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import java.util.Arrays;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/** Construye JWT de prueba equivalentes a los de un emisor OIDC (claims tenant_id y roles). */
final class Jwts {

    private Jwts() {}

    static JwtRequestPostProcessor of(String subject, String tenantId, String... roles) {
        List<String> r = Arrays.asList(roles);
        return jwt().jwt(j -> {
            j.subject(subject).claim("roles", r);
            if (tenantId != null) j.claim("tenant_id", tenantId);
        }).authorities(r.stream().map(x -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + x)).toList());
    }

    static JwtRequestPostProcessor admin(String tenantId) { return of("admin-" + tenantId, tenantId, "TENANT_ADMIN"); }

    static JwtRequestPostProcessor platform() { return of("platform", null, "SUPER_ADMIN"); }
}
