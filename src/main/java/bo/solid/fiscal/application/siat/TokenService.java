package bo.solid.fiscal.application.siat;

import bo.solid.fiscal.application.audit.AuditService;
import bo.solid.fiscal.application.context.RequestContext;
import bo.solid.fiscal.application.crypto.SecretCipher;
import bo.solid.fiscal.application.org.port.CompanyRepository;
import bo.solid.fiscal.application.org.port.FiscalTokenRepository;
import bo.solid.fiscal.application.tenancy.TenantContext;
import bo.solid.fiscal.domain.org.FiscalToken;
import bo.solid.fiscal.domain.shared.FiscalException;
import bo.solid.fiscal.domain.shared.Uuid7;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registro y resolucion del Token Delegado del contribuyente. El texto plano solo existe en memoria, brevemente. */
@Service
public class TokenService {

    public static final int MAX_TOKEN_LENGTH = 4096;

    public record Status(boolean registered, Instant expiresAt, String status, boolean expired) {}

    private final FiscalTokenRepository tokens;
    private final CompanyRepository companies;
    private final SecretCipher cipher;
    private final AuditService audit;
    private final Clock clock;

    public TokenService(FiscalTokenRepository tokens, CompanyRepository companies, SecretCipher cipher,
                        AuditService audit, Clock clock) {
        this.tokens = tokens;
        this.companies = companies;
        this.cipher = cipher;
        this.audit = audit;
        this.clock = clock;
    }

    /** AAD que liga el ciphertext a su tenant y empresa. */
    static String aad(UUID tenantId, UUID companyId) {
        return "fiscal-token:" + tenantId + ":" + companyId;
    }

    @Transactional
    public Status register(UUID companyId, String plaintextToken, Instant expiresAt) {
        UUID tenantId = TenantContext.require();
        companies.findCompany(companyId)
                .orElseThrow(() -> FiscalException.notFound("ORG_002", "Empresa no encontrada"));
        if (plaintextToken == null || plaintextToken.isBlank() || plaintextToken.length() > MAX_TOKEN_LENGTH) {
            throw FiscalException.unprocessable("SIAT_CFG_001", "Token invalido (1.." + MAX_TOKEN_LENGTH + " caracteres)");
        }
        Instant now = clock.instant();
        if (expiresAt == null || !expiresAt.isAfter(now)) {
            throw FiscalException.unprocessable("SIAT_CFG_003", "La fecha de vencimiento del token debe ser futura");
        }
        String stored = cipher.encrypt(plaintextToken.strip(), aad(tenantId, companyId));
        FiscalToken token = new FiscalToken(Uuid7.next(), tenantId, companyId, stored, cipher.keyIdOf(stored), expiresAt,
                "ACTIVE", now, RequestContext.currentOrSystem().actorId());
        tokens.replaceActive(token);
        // Auditoria SIN el token: solo metadatos.
        audit.record(tenantId, "FiscalTokenRegistered", "COMPANY", companyId, null, null,
                "expiresAt=" + expiresAt + ",keyId=" + token.keyId());
        return new Status(true, expiresAt, "ACTIVE", false);
    }

    @Transactional(readOnly = true)
    public Status status(UUID companyId) {
        TenantContext.require();
        companies.findCompany(companyId)
                .orElseThrow(() -> FiscalException.notFound("ORG_002", "Empresa no encontrada"));
        return tokens.findActive(companyId)
                .map(t -> new Status(true, t.expiresAt(), t.status(), t.isExpired(clock.instant())))
                .orElse(new Status(false, null, null, false));
    }

    /** Descifra el token vigente. Uso interno de la capa de aplicacion; nunca se expone por API. */
    @Transactional(readOnly = true)
    public String resolveActivePlaintext(UUID companyId) {
        UUID tenantId = TenantContext.require();
        FiscalToken token = tokens.findActive(companyId)
                .orElseThrow(() -> FiscalException.unprocessable("SIAT_CFG_004",
                        "La empresa no tiene un token delegado registrado"));
        if (token.isExpired(clock.instant())) {
            throw FiscalException.unprocessable("SIAT_CFG_005", "El token delegado esta vencido; genere uno nuevo en el portal SIAT");
        }
        return cipher.decrypt(token.ciphertext(), aad(tenantId, companyId));
    }
}
