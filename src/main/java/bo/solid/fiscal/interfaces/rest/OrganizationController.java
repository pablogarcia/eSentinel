package bo.solid.fiscal.interfaces.rest;

import bo.solid.fiscal.application.org.OrganizationService;
import bo.solid.fiscal.domain.org.Branch;
import bo.solid.fiscal.domain.org.Company;
import bo.solid.fiscal.domain.org.Environment;
import bo.solid.fiscal.domain.org.Modality;
import bo.solid.fiscal.domain.org.PointOfSale;
import bo.solid.fiscal.domain.org.Tenant;
import bo.solid.fiscal.interfaces.security.Roles;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class OrganizationController {

    private final OrganizationService service;
    private final IdempotencySupport idempotency;

    OrganizationController(OrganizationService service, IdempotencySupport idempotency) {
        this.service = service;
        this.idempotency = idempotency;
    }

    // ---- DTOs (separados del dominio)
    record CreateTenantRequest(@NotBlank @Size(max = 200) String name) {}
    record TenantResponse(UUID id, String name, String status) {}

    record CreateCompanyRequest(@Min(1) @Max(9_999_999_999_999L) long nit,
                                @NotBlank @Size(max = 200) String razonSocial,
                                @NotNull Environment environment,
                                Modality modality,
                                @Size(max = 100) String systemCode) {}
    record CompanyResponse(UUID id, long nit, String razonSocial, Environment environment, Modality modality,
                           String systemCode, String status) {}

    record CreateBranchRequest(@Min(0) int codigoSucursal, @NotBlank @Size(max = 200) String nombre,
                               @NotBlank @Size(max = 500) String direccion,
                               @NotBlank @Size(max = 200) String municipio, @Size(max = 50) String telefono) {}
    record BranchResponse(UUID id, UUID companyId, int codigoSucursal, String nombre, String direccion,
                          String municipio, String telefono) {}

    record CreatePointOfSaleRequest(@Min(1) int codigoPuntoVenta, @NotBlank @Size(max = 200) String nombre,
                                    Integer tipoPuntoVenta) {}
    record PointOfSaleResponse(UUID id, UUID branchId, int codigoPuntoVenta, String nombre, Integer tipoPuntoVenta) {}

    // ---- endpoints
    @PostMapping("/tenants")
    @PreAuthorize(Roles.PLATFORM)
    ResponseEntity<TenantResponse> createTenant(@Valid @RequestBody CreateTenantRequest req) {
        Tenant t = service.createTenant(req.name());
        return ResponseEntity.created(URI.create("/api/v1/tenants/" + t.id()))
                .body(new TenantResponse(t.id(), t.name(), t.status()));
    }

    @PostMapping("/companies")
    @PreAuthorize(Roles.CAN_ADMINISTER)
    ResponseEntity<String> createCompany(@RequestHeader(value = IdempotencySupport.HEADER, required = false) String key,
                                         @Valid @RequestBody CreateCompanyRequest req) {
        return idempotency.run(key, "POST", "/api/v1/companies", req, () -> {
            Modality modality = req.modality() == null ? Modality.ELECTRONIC_ONLINE : req.modality();
            Company c = service.registerCompany(req.nit(), req.razonSocial(), req.environment(), modality,
                    req.systemCode());
            return new IdempotencySupport.Created(201, "/api/v1/companies/" + c.id(), toResponse(c));
        });
    }

    @GetMapping("/companies")
    @PreAuthorize(Roles.CAN_READ)
    List<CompanyResponse> listCompanies() {
        return service.listCompanies().stream().map(OrganizationController::toResponse).toList();
    }

    @GetMapping("/companies/{id}")
    @PreAuthorize(Roles.CAN_READ)
    CompanyResponse getCompany(@PathVariable UUID id) {
        return toResponse(service.getCompany(id));
    }

    @PostMapping("/companies/{companyId}/branches")
    @PreAuthorize(Roles.CAN_ADMINISTER)
    ResponseEntity<String> addBranch(@RequestHeader(value = IdempotencySupport.HEADER, required = false) String key,
                                     @PathVariable UUID companyId, @Valid @RequestBody CreateBranchRequest req) {
        return idempotency.run(key, "POST", "/api/v1/companies/" + companyId + "/branches", req, () -> {
            Branch b = service.addBranch(companyId, req.codigoSucursal(), req.nombre(), req.direccion(),
                    req.municipio(), req.telefono());
            return new IdempotencySupport.Created(201, "/api/v1/branches/" + b.id(), toResponse(b));
        });
    }

    @GetMapping("/companies/{companyId}/branches")
    @PreAuthorize(Roles.CAN_READ)
    List<BranchResponse> listBranches(@PathVariable UUID companyId) {
        return service.listBranches(companyId).stream().map(OrganizationController::toResponse).toList();
    }

    @PostMapping("/branches/{branchId}/points-of-sale")
    @PreAuthorize(Roles.CAN_ADMINISTER)
    ResponseEntity<String> addPointOfSale(@RequestHeader(value = IdempotencySupport.HEADER, required = false) String key,
                                          @PathVariable UUID branchId,
                                          @Valid @RequestBody CreatePointOfSaleRequest req) {
        return idempotency.run(key, "POST", "/api/v1/branches/" + branchId + "/points-of-sale", req, () -> {
            PointOfSale p = service.addPointOfSale(branchId, req.codigoPuntoVenta(), req.nombre(), req.tipoPuntoVenta());
            return new IdempotencySupport.Created(201, "/api/v1/points-of-sale/" + p.id(), toResponse(p));
        });
    }

    @GetMapping("/branches/{branchId}/points-of-sale")
    @PreAuthorize(Roles.CAN_READ)
    List<PointOfSaleResponse> listPointsOfSale(@PathVariable UUID branchId) {
        return service.listPointsOfSale(branchId).stream().map(OrganizationController::toResponse).toList();
    }

    private static CompanyResponse toResponse(Company c) {
        return new CompanyResponse(c.id(), c.nit().value(), c.razonSocial(), c.environment(), c.modality(),
                c.systemCode(), c.status());
    }

    private static BranchResponse toResponse(Branch b) {
        return new BranchResponse(b.id(), b.companyId(), b.codigoSucursal(), b.nombre(), b.direccion(),
                b.municipio(), b.telefono());
    }

    private static PointOfSaleResponse toResponse(PointOfSale p) {
        return new PointOfSaleResponse(p.id(), p.branchId(), p.codigoPuntoVenta(), p.nombre(), p.tipoPuntoVenta());
    }
}
