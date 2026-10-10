package bo.solid.fiscal.application.org.port;

import bo.solid.fiscal.domain.org.PointOfSale;
import java.util.List;
import java.util.UUID;

public interface PointOfSaleRepository {
    void insert(PointOfSale pointOfSale);
    List<PointOfSale> findPointsOfSaleByBranch(UUID branchId);
}
