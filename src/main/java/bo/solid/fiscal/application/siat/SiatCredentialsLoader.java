package bo.solid.fiscal.application.siat;

import bo.solid.fiscal.application.org.port.CompanyRepository;
import bo.solid.fiscal.application.tenancy.TenantContext;
import bo.solid.fiscal.domain.org.Company;
import bo.solid.fiscal.domain.shared.FiscalException;
import bo.solid.fiscal.domain.siat.SiatCredentials;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Arma las credenciales SIAT de una empresa en una TX corta de solo lectura (no se mantiene abierta durante la llamada). */
@Service
public class SiatCredentialsLoader {

    private final CompanyRepository companies;
    private final TokenService tokens;

    public SiatCredentialsLoader(CompanyRepository companies, TokenService tokens) {
        this.companies = companies;
        this.tokens = tokens;
    }

    @Transactional(readOnly = true)
    public SiatCredentials load(UUID companyId, boolean requireSystemCode) {
        TenantContext.require();
        Company c = companies.findCompany(companyId)
                .orElseThrow(() -> FiscalException.notFound("ORG_002", "Empresa no encontrada"));
        if (requireSystemCode && (c.systemCode() == null || c.systemCode().isBlank())) {
            throw FiscalException.unprocessable("SIAT_CFG_006",
                    "La empresa no tiene codigoSistema (se obtiene al registrar el sistema en el portal SIAT)");
        }
        return new SiatCredentials(c.environment(), c.nit().value(), c.systemCode(), c.modality(),
                tokens.resolveActivePlaintext(companyId));
    }
}
