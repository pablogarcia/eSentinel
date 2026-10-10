package bo.solid.fiscal.interfaces.security;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;

/** API stateless protegida por JWT (OIDC). El emisor se configura con spring.security.oauth2.resourceserver.jwt.*. */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
class SecurityConfig {

    @Bean
    SecurityFilterChain apiChain(HttpSecurity http, AuthenticationEntryPoint entryPoint,
                                 AccessDeniedHandler deniedHandler, JwtAuthenticationConverter converter,
                                 ApiErrorWriter errors,
                                 @Value("${solid.fiscal.security.tenant-claim:tenant_id}") String tenantClaim)
            throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole(Roles.SUPER_ADMIN)
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .oauth2ResourceServer(o -> o
                        .jwt(j -> j.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(entryPoint).accessDeniedHandler(deniedHandler))
                .addFilterAfter(new RequestContextFilter(tenantClaim, errors), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter(
            @Value("${solid.fiscal.security.roles-claim:roles}") String rolesClaim) {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> authorities(jwt, rolesClaim));
        return converter;
    }

    private static Collection<GrantedAuthority> authorities(Jwt jwt, String rolesClaim) {
        List<String> roles = jwt.getClaimAsStringList(rolesClaim);
        if (roles == null) return List.of();
        return roles.stream().map(r -> (GrantedAuthority) new SimpleGrantedAuthority("ROLE_" + r))
                .collect(Collectors.toList());
    }

    @Bean
    AuthenticationEntryPoint apiAuthenticationEntryPoint(ApiErrorWriter errors) {
        return (req, res, ex) -> errors.write(res, HttpStatus.UNAUTHORIZED.value(), "SECURITY_001",
                "Autenticacion requerida o token invalido");
    }

    @Bean
    AccessDeniedHandler apiAccessDeniedHandler(ApiErrorWriter errors) {
        return (req, res, ex) -> errors.write(res, HttpStatus.FORBIDDEN.value(), "SECURITY_004", "Acceso denegado");
    }
}
