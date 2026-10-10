package bo.solid.fiscal.application.codes;

import bo.solid.fiscal.application.audit.AuditService;
import bo.solid.fiscal.application.codes.port.AccessCodeRepository;
import bo.solid.fiscal.application.org.port.BranchRepository;
import bo.solid.fiscal.application.org.port.CompanyRepository;
import bo.solid.fiscal.application.org.port.PointOfSaleRepository;
import bo.solid.fiscal.application.outbox.OutboxMessage;
import bo.solid.fiscal.application.outbox.port.OutboxWriter;
import bo.solid.fiscal.application.siat.SiatCallExecutor;
import bo.solid.fiscal.application.siat.SiatCredentialsLoader;
import bo.solid.fiscal.application.siat.port.SiatPort;
import bo.solid.fiscal.application.tenancy.TenantContext;
import bo.solid.fiscal.domain.org.Branch;
import bo.solid.fiscal.domain.org.Company;
import bo.solid.fiscal.domain.org.Cufd;
import bo.solid.fiscal.domain.org.Cuis;
import bo.solid.fiscal.domain.org.PointOfSale;
import bo.solid.fiscal.domain.shared.FiscalException;
import bo.solid.fiscal.domain.shared.Uuid7;
import bo.solid.fiscal.domain.siat.BranchScope;
import bo.solid.fiscal.domain.siat.CufdResult;
import bo.solid.fiscal.domain.siat.CuisResult;
import bo.solid.fiscal.domain.siat.SiatCredentials;
import bo.solid.fiscal.domain.siat.SiatDateTime;
import bo.solid.fiscal.domain.siat.SiatMessage;
import bo.solid.fiscal.domain.siat.SiatScope;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * CUIS y CUFD (ADR-0007, ADR-0012). Toda obtencion ocurre dentro de una transaccion que primero toma el candado del
 * alcance y RELEE el estado: dos instancias simultaneas producen una sola solicitud al SIAT; la segunda reutiliza el codigo.
 * La emision NUNCA espera una renovacion mientras el CUFD actual siga vigente.
 */
@Service
public class AccessCodeService {

    private static final Logger log = LoggerFactory.getLogger(AccessCodeService.class);

    private final AccessCodeRepository codes;
    private final CompanyRepository companies;
    private final BranchRepository branches;
    private final PointOfSaleRepository pointsOfSale;
    private final SiatCredentialsLoader credentials;
    private final SiatCallExecutor executor;
    private final SiatPort siat;
    private final AuditService audit;
    private final OutboxWriter outbox;
    private final MeterRegistry meters;
    private final Clock clock;
    private final Duration cufdLeadTime;
    private final Duration maxClockSkew;

    public AccessCodeService(AccessCodeRepository codes, CompanyRepository companies, BranchRepository branches,
                             PointOfSaleRepository pointsOfSale, SiatCredentialsLoader credentials,
                             SiatCallExecutor executor, SiatPort siat, AuditService audit, OutboxWriter outbox,
                             MeterRegistry meters, Clock clock,
                             @Value("${solid.fiscal.codes.cufd-lead-time:PT90M}") Duration cufdLeadTime,
                             @Value("${solid.fiscal.codes.max-clock-skew:PT2M}") Duration maxClockSkew) {
        this.codes = codes;
        this.companies = companies;
        this.branches = branches;
        this.pointsOfSale = pointsOfSale;
        this.credentials = credentials;
        this.executor = executor;
        this.siat = siat;
        this.audit = audit;
        this.outbox = outbox;
        this.meters = meters;
        this.clock = clock;
        this.cufdLeadTime = cufdLeadTime;
        this.maxClockSkew = maxClockSkew;
    }

    // ------------------------------------------------------------------ lecturas (sin tocar el SIAT)

    @Transactional(readOnly = true)
    public Cuis activeCuis(UUID branchId, UUID posId) {
        TenantContext.require();
        resolve(branchId, posId);
        return codes.findActiveCuis(branchId, posId)
                .filter(c -> c.isValidAt(clock.instant()))
                .orElseThrow(() -> FiscalException.notFound("CODES_001", "No hay un CUIS vigente para ese alcance"));
    }

    @Transactional(readOnly = true)
    public List<Cufd> cufdHistory(UUID branchId, UUID posId, int limit) {
        TenantContext.require();
        resolve(branchId, posId);
        return codes.cufdHistory(branchId, posId, Math.min(Math.max(limit, 1), 100));
    }

    // ------------------------------------------------------------------ obtencion

    /** CUIS vigente del alcance; lo solicita solo si falta, vencio o esta en la ventana de renovacion (5 dias) / force. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Cuis ensureCuis(UUID branchId, UUID posId, boolean force) {
        TenantContext.require();
        Scope sc = resolve(branchId, posId);
        codes.lockScope(branchId, posId);                       // 1. candado
        Instant now = clock.instant();
        var existing = codes.findActiveCuis(branchId, posId);   // 2. releer
        if (!force && existing.isPresent() && existing.get().isValidAt(now) && !existing.get().isInRenewalWindow(now)) {
            return existing.get();
        }
        SiatCredentials cred = credentials.load(sc.company.id(), true);
        CuisResult r = executor.execute(sc.company.id(), "FacturacionCodigos", "cuis", null,    // 3. SIAT
                () -> siat.requestCuis(cred, new BranchScope(sc.branch.codigoSucursal(), sc.posCode())));
        if (!r.ok() || r.codigo() == null || r.validUntil() == null) {
            meters.counter("cuis.renewals", "result", "rejected").increment();
            throw FiscalException.siatRejected("El SIAT no otorgo el CUIS", describe(r.messages()));
        }
        Cuis cuis = new Cuis(Uuid7.next(), sc.company.tenantId(), sc.company.id(), branchId, posId, r.codigo(),
                r.validUntil(), "ACTIVE", now);
        codes.replaceActiveCuis(cuis);
        audit.record(cuis.tenantId(), "CuisObtained", "CUIS", cuis.id(), null, null, "validUntil=" + cuis.validUntil());
        outbox.append(new OutboxMessage(cuis.tenantId(), "CUIS", cuis.id(), "CuisObtained",
                Map.of("tenantId", cuis.tenantId(), "id", cuis.id(), "branchId", branchId, "validUntil", cuis.validUntil().toString())));
        meters.counter("cuis.renewals", "result", "ok").increment();
        return cuis;
    }

    /**
     * CUFD utilizable: devuelve el vigente SIN llamar al SIAT mientras no haya vencido. Si no hay o ya vencio, lo obtiene
     * (sincronizando antes fecha/hora, como recomienda el SIN). La renovacion anticipada la hace {@link #renewIfDue}.
     */
    public Cufd currentCufd(UUID branchId, UUID posId) {
        TenantContext.require();
        Instant now = clock.instant();
        var current = readActiveCufd(branchId, posId);
        if (current.isPresent() && current.get().isValidAt(now)) {
            return current.get();
        }
        return obtainCufd(branchId, posId, false);
    }

    /** Renovacion proactiva: CUIS (ventana 5 dias) y luego CUFD si faltan menos de cufdLeadTime. Idempotente. */
    public boolean renewIfDue(UUID branchId, UUID posId) {
        TenantContext.require();
        Cuis before = readActiveCuis(branchId, posId).orElse(null);
        Instant now = clock.instant();
        boolean cuisDue = before == null || !before.isValidAt(now) || before.isInRenewalWindow(now);
        if (cuisDue) ensureCuis(branchId, posId, false);
        var cufd = readActiveCufd(branchId, posId);
        boolean cufdDue = cufd.isEmpty() || cufd.get().needsRenewal(clock.instant(), cufdLeadTime);
        if (cufdDue) obtainCufd(branchId, posId, true);
        return cuisDue || cufdDue;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Cufd obtainCufd(UUID branchId, UUID posId, boolean renewingEarly) {
        TenantContext.require();
        Scope sc = resolve(branchId, posId);
        codes.lockScope(branchId, posId);                                  // 1. candado
        Instant now = clock.instant();
        var existing = codes.findActiveCufd(branchId, posId);              // 2. releer
        if (existing.isPresent() && (renewingEarly ? !existing.get().needsRenewal(now, cufdLeadTime)
                : existing.get().isValidAt(now))) {
            meters.counter("cufd.renewals", "result", "skipped_already_fresh").increment();
            return existing.get();                                         // otra instancia ya lo renovo
        }
        Cuis cuis = codes.findActiveCuis(branchId, posId).filter(c -> c.isValidAt(now)).orElse(null);
        if (cuis == null) cuis = ensureCuisWithinLock(sc, now);
        SiatCredentials cred = credentials.load(sc.company.id(), true);
        SiatScope scope = new SiatScope(sc.branch.codigoSucursal(), sc.posCode(), cuis.codigo());
        checkClock(sc.company.id(), cred, scope);                          // 3. fecha/hora (recomendado por el SIN)
        CufdResult r = executor.execute(sc.company.id(), "FacturacionCodigos", "cufd", null,    // 4. SIAT
                () -> siat.requestCufd(cred, scope));
        if (!r.ok() || r.codigo() == null || r.codigoControl() == null || r.validUntil() == null) {
            meters.counter("cufd.renewals", "result", "rejected").increment();
            throw FiscalException.siatRejected("El SIAT no otorgo el CUFD", describe(r.messages()));
        }
        Cufd cufd = new Cufd(Uuid7.next(), sc.company.tenantId(), sc.company.id(), branchId, posId, cuis.id(), r.codigo(),
                r.codigoControl(), r.direccion(), r.validUntil(), "ACTIVE", now);
        codes.replaceActiveCufd(cufd);
        audit.record(cufd.tenantId(), "CufdObtained", "CUFD", cufd.id(), null, null, "validUntil=" + cufd.validUntil());
        outbox.append(new OutboxMessage(cufd.tenantId(), "CUFD", cufd.id(), "CufdObtained",
                Map.of("tenantId", cufd.tenantId(), "id", cufd.id(), "branchId", branchId, "validUntil", cufd.validUntil().toString())));
        meters.counter("cufd.renewals", "result", "ok").increment();
        return cufd;
    }

    // ------------------------------------------------------------------ internos

    /** Pide el CUIS dentro del candado que ya tenemos (obtainCufd), sin abrir otra transaccion ni tomar otro candado. */
    private Cuis ensureCuisWithinLock(Scope sc, Instant now) {
        SiatCredentials cred = credentials.load(sc.company.id(), true);
        CuisResult r = executor.execute(sc.company.id(), "FacturacionCodigos", "cuis", null,
                () -> siat.requestCuis(cred, new BranchScope(sc.branch.codigoSucursal(), sc.posCode())));
        if (!r.ok() || r.codigo() == null || r.validUntil() == null) {
            throw FiscalException.siatRejected("El SIAT no otorgo el CUIS", describe(r.messages()));
        }
        Cuis cuis = new Cuis(Uuid7.next(), sc.company.tenantId(), sc.company.id(), sc.branch.id(), sc.posId, r.codigo(),
                r.validUntil(), "ACTIVE", now);
        codes.replaceActiveCuis(cuis);
        audit.record(cuis.tenantId(), "CuisObtained", "CUIS", cuis.id(), null, null, "validUntil=" + cuis.validUntil());
        meters.counter("cuis.renewals", "result", "ok").increment();
        return cuis;
    }

    private void checkClock(UUID companyId, SiatCredentials cred, SiatScope scope) {
        try {
            SiatDateTime t = executor.execute(companyId, "FacturacionSincronizacion", "sincronizarFechaHora", null,
                    () -> siat.synchronizeDateTime(cred, scope));
            if (t.ok() && t.serverTime() != null) {
                Instant server = t.serverTime().atZone(java.time.ZoneId.of("America/La_Paz")).toInstant();
                long skew = Duration.between(clock.instant(), server).getSeconds();
                meters.gauge("siat.clock.skew.seconds", new java.util.concurrent.atomic.AtomicLong(skew), java.util.concurrent.atomic.AtomicLong::get);
                if (Math.abs(skew) > maxClockSkew.getSeconds()) {
                    log.warn("Reloj local desfasado {} s respecto del SIAT (maximo tolerado {} s)", skew, maxClockSkew.getSeconds());
                }
            }
        } catch (FiscalException e) {
            // La sincronizacion es recomendada, no bloqueante: si falla se registra y se sigue con el CUFD.
            log.warn("No se pudo sincronizar fecha/hora antes del CUFD: {}", e.code());
        }
    }

    @Transactional(readOnly = true)
    java.util.Optional<Cuis> readActiveCuis(UUID branchId, UUID posId) { return codes.findActiveCuis(branchId, posId); }

    @Transactional(readOnly = true)
    java.util.Optional<Cufd> readActiveCufd(UUID branchId, UUID posId) { return codes.findActiveCufd(branchId, posId); }

    private record Scope(Company company, Branch branch, UUID posId, int posCode) {}

    private Scope resolve(UUID branchId, UUID posId) {
        Branch b = branches.findBranch(branchId).orElseThrow(() -> FiscalException.notFound("ORG_003", "Sucursal no encontrada"));
        Company c = companies.findCompany(b.companyId()).orElseThrow(() -> FiscalException.notFound("ORG_002", "Empresa no encontrada"));
        int posCode = 0;
        if (posId != null) {
            PointOfSale p = pointsOfSale.findPointsOfSaleByBranch(branchId).stream().filter(x -> x.id().equals(posId)).findFirst()
                    .orElseThrow(() -> FiscalException.notFound("ORG_004", "Punto de venta no encontrado en la sucursal"));
            posCode = p.codigoPuntoVenta();
        }
        return new Scope(c, b, posId, posCode);
    }

    private static List<String> describe(List<SiatMessage> m) {
        return m.stream().map(x -> x.code() + ": " + x.description()).toList();
    }
}
