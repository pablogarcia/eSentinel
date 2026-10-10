package bo.solid.fiscal.interfaces.rest;

import bo.solid.fiscal.application.org.OrganizationService;
import bo.solid.fiscal.application.siat.SiatConnectionService;
import bo.solid.fiscal.application.siat.TokenService;
import bo.solid.fiscal.domain.org.Company;
import bo.solid.fiscal.domain.siat.SiatMessage;
import bo.solid.fiscal.interfaces.security.Roles;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Configuracion SIAT de una empresa: codigoSistema, token delegado (write-only) y prueba de conectividad. */
@RestController
@RequestMapping("/api/v1/companies/{companyId}")
class SiatController {

    private final OrganizationService organization;
    private final TokenService tokens;
    private final SiatConnectionService connection;

    SiatController(OrganizationService organization, TokenService tokens, SiatConnectionService connection) {
        this.organization = organization;
        this.tokens = tokens;
        this.connection = connection;
    }

    /** El token es write-only: jamas se devuelve ni se imprime (toString enmascarado). */
    record RegisterTokenRequest(@NotBlank @Size(max = TokenService.MAX_TOKEN_LENGTH) String token,
                                @NotNull @Future Instant expiresAt) {
        @Override
        public String toString() { return "RegisterTokenRequest[token=***, expiresAt=" + expiresAt + "]"; }
    }

    record TokenStatusResponse(boolean registered, Instant expiresAt, String status, boolean expired) {}

    record SystemCodeRequest(@NotBlank @Size(max = 100) String systemCode) {}

    record SystemCodeResponse(UUID id, String systemCode) {}

    record ConnectivityResponse(boolean reachable, boolean serviceOk, List<SiatMessage> messages, long latencyMs) {}

    @PutMapping("/siat-token")
    @PreAuthorize(Roles.CAN_ADMINISTER)
    TokenStatusResponse registerToken(@PathVariable UUID companyId, @Valid @RequestBody RegisterTokenRequest req) {
        return toResponse(tokens.register(companyId, req.token(), req.expiresAt()));
    }

    @GetMapping("/siat-token")
    @PreAuthorize(Roles.CAN_ADMINISTER)
    TokenStatusResponse tokenStatus(@PathVariable UUID companyId) {
        return toResponse(tokens.status(companyId));
    }

    @PutMapping("/system-code")
    @PreAuthorize(Roles.CAN_ADMINISTER)
    SystemCodeResponse setSystemCode(@PathVariable UUID companyId, @Valid @RequestBody SystemCodeRequest req) {
        Company c = organization.setSystemCode(companyId, req.systemCode());
        return new SystemCodeResponse(c.id(), c.systemCode());
    }

    @PostMapping("/siat/connectivity-check")
    @PreAuthorize(Roles.CAN_ADMINISTER)
    ConnectivityResponse connectivity(@PathVariable UUID companyId) {
        SiatConnectionService.ConnectivityResult r = connection.checkCommunication(companyId);
        return new ConnectivityResponse(r.reachable(), r.serviceOk(), r.messages(), r.latencyMs());
    }

    private static TokenStatusResponse toResponse(TokenService.Status s) {
        return new TokenStatusResponse(s.registered(), s.expiresAt(), s.status(), s.expired());
    }
}
