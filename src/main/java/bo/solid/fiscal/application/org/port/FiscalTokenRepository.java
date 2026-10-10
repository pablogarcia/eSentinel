package bo.solid.fiscal.application.org.port;

import bo.solid.fiscal.domain.org.FiscalToken;
import java.util.Optional;
import java.util.UUID;

public interface FiscalTokenRepository {
    /** Revoca el token ACTIVE previo (si existe) e inserta el nuevo, atomico en la TX actual. */
    void replaceActive(FiscalToken token);

    Optional<FiscalToken> findActive(UUID companyId);
}
