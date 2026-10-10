package bo.solid.fiscal.application.org;

import bo.solid.fiscal.application.audit.AuditService;
import bo.solid.fiscal.application.org.port.BranchRepository;
import bo.solid.fiscal.application.org.port.CompanyRepository;
import bo.solid.fiscal.application.org.port.PointOfSaleRepository;
import bo.solid.fiscal.application.org.port.TenantRepository;
import bo.solid.fiscal.application.outbox.OutboxMessage;
import bo.solid.fiscal.application.outbox.port.OutboxWriter;
import bo.solid.fiscal.application.tenancy.TenantContext;
import bo.solid.fiscal.application.tenancy.TenantSession;
import bo.solid.fiscal.domain.org.Branch;
import bo.solid.fiscal.domain.org.Company;
import bo.solid.fiscal.domain.org.Environment;
import bo.solid.fiscal.domain.org.Modality;
import bo.solid.fiscal.domain.org.Nit;
import bo.solid.fiscal.domain.org.PointOfSale;
import bo.solid.fiscal.domain.org.Tenant;
import bo.solid.fiscal.domain.shared.FiscalException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Casos de uso de organizacion: tenant, empresa (NIT), sucursal y punto de venta.
 * Cada metodo es una transaccion; la infraestructura activa RLS con el tenant de TenantContext.
 */
@Service
public class OrganizationService {

    private final TenantRepository tenants;
    private final CompanyRepository companies;
    private final BranchRepository branches;
    private final PointOfSaleRepository pointsOfSale;
    private final AuditService audit;
    private final OutboxWriter outbox;
    private final TenantSession tenantSession;

    public OrganizationService(TenantRepository tenants, CompanyRepository companies,
                               BranchRepository branches, PointOfSaleRepository pointsOfSale,
                               AuditService audit, OutboxWriter outbox, TenantSession tenantSession) {
        this.tenants = tenants;
        this.companies = companies;
        this.branches = branches;
        this.pointsOfSale = pointsOfSale;
        this.audit = audit;
        this.outbox = outbox;
        this.tenantSession = tenantSession;
    }

    /** Auditoria + evento de outbox en la misma TX que el cambio (ADR-0009). */
    private void emit(UUID tenantId, String eventType, String entityType, UUID entityId, String newState,
                      Map<String, Object> payload) {
        audit.record(tenantId, eventType, entityType, entityId, null, null, newState);
        Map<String, Object> body = new LinkedHashMap<>(payload);
        body.put("tenantId", tenantId);
        body.put("id", entityId);
        outbox.append(new OutboxMessage(tenantId, entityType, entityId, eventType, body));
    }

    /** Operacion de plataforma (SUPER_ADMIN). No requiere tenant en contexto. */
    @Transactional
    public Tenant createTenant(String name) {
        Tenant tenant = Tenant.create(name);
        tenants.insert(tenant);
        tenantSession.bind(tenant.id());   // la TX queda enlazada al tenant nuevo para auditoria/outbox bajo RLS
        emit(tenant.id(), "TenantCreated", "TENANT", tenant.id(), tenant.status(), Map.of("name", tenant.name()));
        return tenant;
    }

    @Transactional(readOnly = true)
    public Tenant getCurrentTenant() {
        return tenants.findTenant(TenantContext.require())
                .orElseThrow(() -> FiscalException.notFound("ORG_001", "Tenant no encontrado"));
    }

    @Transactional
    public Company registerCompany(long nit, String razonSocial, Environment environment, Modality modality,
                                   String systemCode) {
        UUID tenantId = TenantContext.require();
        if (!modality.supported()) {
            throw FiscalException.unprocessable("ORG_010",
                    "Modalidad " + modality + " aun no soportada; solo ELECTRONIC_ONLINE (ADR-0003)");
        }
        Company company = Company.register(tenantId, new Nit(nit), razonSocial, environment, modality, systemCode);
        companies.insert(company);
        emit(tenantId, "CompanyRegistered", "COMPANY", company.id(), company.status(),
                Map.of("nit", company.nit().value(), "environment", environment.name(), "modality", modality.name()));
        return company;
    }

    @Transactional(readOnly = true)
    public Company getCompany(UUID id) {
        TenantContext.require();
        return companies.findCompany(id)
                .orElseThrow(() -> FiscalException.notFound("ORG_002", "Empresa no encontrada"));
    }

    @Transactional(readOnly = true)
    public List<Company> listCompanies() {
        TenantContext.require();
        return companies.findAllCompanies();
    }

    @Transactional
    public Branch addBranch(UUID companyId, int codigoSucursal, String nombre, String direccion,
                            String municipio, String telefono) {
        UUID tenantId = TenantContext.require();
        companies.findCompany(companyId)
                .orElseThrow(() -> FiscalException.notFound("ORG_002", "Empresa no encontrada"));
        Branch branch = Branch.create(tenantId, companyId, codigoSucursal, nombre, direccion, municipio, telefono);
        branches.insert(branch);
        emit(tenantId, "BranchAdded", "BRANCH", branch.id(), "ACTIVE",
                Map.of("companyId", companyId, "codigoSucursal", codigoSucursal));
        return branch;
    }

    @Transactional(readOnly = true)
    public List<Branch> listBranches(UUID companyId) {
        TenantContext.require();
        companies.findCompany(companyId)
                .orElseThrow(() -> FiscalException.notFound("ORG_002", "Empresa no encontrada"));
        return branches.findBranchesByCompany(companyId);
    }

    @Transactional
    public PointOfSale addPointOfSale(UUID branchId, int codigoPuntoVenta, String nombre, Integer tipoPuntoVenta) {
        UUID tenantId = TenantContext.require();
        branches.findBranch(branchId)
                .orElseThrow(() -> FiscalException.notFound("ORG_003", "Sucursal no encontrada"));
        PointOfSale pos = PointOfSale.create(tenantId, branchId, codigoPuntoVenta, nombre, tipoPuntoVenta);
        pointsOfSale.insert(pos);
        emit(tenantId, "PointOfSaleAdded", "POINT_OF_SALE", pos.id(), "ACTIVE",
                Map.of("branchId", branchId, "codigoPuntoVenta", codigoPuntoVenta));
        return pos;
    }

    @Transactional(readOnly = true)
    public List<PointOfSale> listPointsOfSale(UUID branchId) {
        TenantContext.require();
        branches.findBranch(branchId)
                .orElseThrow(() -> FiscalException.notFound("ORG_003", "Sucursal no encontrada"));
        return pointsOfSale.findPointsOfSaleByBranch(branchId);
    }
}
