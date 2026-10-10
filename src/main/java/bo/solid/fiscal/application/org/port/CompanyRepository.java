package bo.solid.fiscal.application.org.port;

import bo.solid.fiscal.domain.org.Company;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository {
    /** @throws bo.solid.fiscal.domain.shared.FiscalException CONFLICT si (tenant, nit, ambiente) ya existe. */
    void insert(Company company);
    Optional<Company> findCompany(UUID id);
    List<Company> findAllCompanies();
}
