package bo.solid.fiscal.infrastructure.persistence;

import bo.solid.fiscal.application.org.port.BranchRepository;
import bo.solid.fiscal.application.org.port.CompanyRepository;
import bo.solid.fiscal.application.org.port.PointOfSaleRepository;
import bo.solid.fiscal.application.org.port.TenantRepository;
import bo.solid.fiscal.domain.org.Branch;
import bo.solid.fiscal.domain.org.Company;
import bo.solid.fiscal.domain.org.Environment;
import bo.solid.fiscal.domain.org.Modality;
import bo.solid.fiscal.domain.org.Nit;
import bo.solid.fiscal.domain.org.PointOfSale;
import bo.solid.fiscal.domain.org.Tenant;
import bo.solid.fiscal.domain.shared.FiscalException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Adaptador JDBC. Sin logica fiscal: solo mapeo y traduccion de errores de integridad. */
@Repository
class JdbcOrganizationRepository
        implements TenantRepository, CompanyRepository, BranchRepository, PointOfSaleRepository {

    private final JdbcClient jdbc;

    JdbcOrganizationRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---- tenant
    @Override
    public void insert(Tenant t) {
        jdbc.sql("INSERT INTO tenant (id, name, status, version, created_at) VALUES (?, ?, ?, ?, ?)")
                .params(t.id(), t.name(), t.status(), t.version(), Timestamp.from(t.createdAt()))
                .update();
    }

    @Override
    public Optional<Tenant> findTenant(UUID id) {
        return jdbc.sql("SELECT id, name, status, version, created_at FROM tenant WHERE id = ?")
                .param(id)
                .query((rs, n) -> new Tenant(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("status"), rs.getLong("version"), rs.getTimestamp("created_at").toInstant()))
                .optional();
    }

    // ---- company
    @Override
    public void insert(Company c) {
        try {
            jdbc.sql("""
                    INSERT INTO company (id, tenant_id, nit, razon_social, environment, modality, system_code,
                                         status, version, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                    .params(c.id(), c.tenantId(), c.nit().value(), c.razonSocial(), c.environment().code(),
                            c.modality().code(), c.systemCode(), c.status(), c.version(),
                            Timestamp.from(c.createdAt()))
                    .update();
        } catch (DuplicateKeyException e) {
            throw FiscalException.conflict("ORG_020", "Ya existe una empresa con ese NIT en ese ambiente");
        }
    }

    @Override
    public Optional<Company> findCompany(UUID id) {
        return jdbc.sql(COMPANY_SELECT + " WHERE id = ?").param(id).query(COMPANY_MAPPER).optional();
    }

    @Override
    public List<Company> findAllCompanies() {
        return jdbc.sql(COMPANY_SELECT + " ORDER BY created_at").query(COMPANY_MAPPER).list();
    }

    private static final String COMPANY_SELECT = """
            SELECT id, tenant_id, nit, razon_social, environment, modality, system_code, status, version, created_at
            FROM company""";

    private static final org.springframework.jdbc.core.RowMapper<Company> COMPANY_MAPPER = (rs, n) -> new Company(
            rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class), new Nit(rs.getLong("nit")),
            rs.getString("razon_social"), Environment.fromCode(rs.getInt("environment")),
            Modality.fromCode(rs.getInt("modality")), rs.getString("system_code"), rs.getString("status"),
            rs.getLong("version"), rs.getTimestamp("created_at").toInstant());

    // ---- branch
    @Override
    public void insert(Branch b) {
        try {
            jdbc.sql("""
                    INSERT INTO branch (id, tenant_id, company_id, codigo_sucursal, nombre, direccion, municipio,
                                        telefono, version, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""")
                    .params(b.id(), b.tenantId(), b.companyId(), b.codigoSucursal(), b.nombre(), b.direccion(),
                            b.municipio(), b.telefono(), b.version(), Timestamp.from(b.createdAt()))
                    .update();
        } catch (DuplicateKeyException e) {
            throw FiscalException.conflict("ORG_021", "Ya existe una sucursal con ese codigo en la empresa");
        }
    }

    @Override
    public Optional<Branch> findBranch(UUID id) {
        return jdbc.sql(BRANCH_SELECT + " WHERE id = ?").param(id).query(BRANCH_MAPPER).optional();
    }

    @Override
    public List<Branch> findBranchesByCompany(UUID companyId) {
        return jdbc.sql(BRANCH_SELECT + " WHERE company_id = ? ORDER BY codigo_sucursal").param(companyId)
                .query(BRANCH_MAPPER).list();
    }

    private static final String BRANCH_SELECT = """
            SELECT id, tenant_id, company_id, codigo_sucursal, nombre, direccion, municipio, telefono, version,
                   created_at FROM branch""";

    private static final org.springframework.jdbc.core.RowMapper<Branch> BRANCH_MAPPER = (rs, n) -> new Branch(
            rs.getObject("id", UUID.class), rs.getObject("tenant_id", UUID.class),
            rs.getObject("company_id", UUID.class), rs.getInt("codigo_sucursal"), rs.getString("nombre"),
            rs.getString("direccion"), rs.getString("municipio"), rs.getString("telefono"),
            rs.getLong("version"), rs.getTimestamp("created_at").toInstant());

    // ---- point of sale
    @Override
    public void insert(PointOfSale p) {
        try {
            jdbc.sql("""
                    INSERT INTO point_of_sale (id, tenant_id, branch_id, codigo_punto_venta, nombre,
                                               tipo_punto_venta, version, created_at)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)""")
                    .params(p.id(), p.tenantId(), p.branchId(), p.codigoPuntoVenta(), p.nombre(),
                            p.tipoPuntoVenta(), p.version(), Timestamp.from(p.createdAt()))
                    .update();
        } catch (DuplicateKeyException e) {
            throw FiscalException.conflict("ORG_022", "Ya existe un punto de venta con ese codigo en la sucursal");
        }
    }

    @Override
    public List<PointOfSale> findPointsOfSaleByBranch(UUID branchId) {
        return jdbc.sql("""
                SELECT id, tenant_id, branch_id, codigo_punto_venta, nombre, tipo_punto_venta, version, created_at
                FROM point_of_sale WHERE branch_id = ? ORDER BY codigo_punto_venta""")
                .param(branchId)
                .query((rs, n) -> new PointOfSale(rs.getObject("id", UUID.class),
                        rs.getObject("tenant_id", UUID.class), rs.getObject("branch_id", UUID.class),
                        rs.getInt("codigo_punto_venta"), rs.getString("nombre"),
                        (Integer) rs.getObject("tipo_punto_venta"), rs.getLong("version"),
                        rs.getTimestamp("created_at").toInstant()))
                .list();
    }
}
