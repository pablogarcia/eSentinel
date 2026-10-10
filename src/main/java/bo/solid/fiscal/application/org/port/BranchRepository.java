package bo.solid.fiscal.application.org.port;

import bo.solid.fiscal.domain.org.Branch;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BranchRepository {
    void insert(Branch branch);
    Optional<Branch> findBranch(UUID id);
    List<Branch> findBranchesByCompany(UUID companyId);
}
