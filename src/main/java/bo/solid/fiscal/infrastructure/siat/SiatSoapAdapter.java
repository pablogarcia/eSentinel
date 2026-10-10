package bo.solid.fiscal.infrastructure.siat;

import bo.solid.fiscal.application.siat.port.SiatPort;
import bo.solid.fiscal.domain.org.Environment;
import bo.solid.fiscal.domain.shared.FiscalException;
import bo.solid.fiscal.domain.siat.BranchScope;
import bo.solid.fiscal.domain.siat.CommunicationStatus;
import bo.solid.fiscal.domain.siat.CufdResult;
import bo.solid.fiscal.domain.siat.CuisResult;
import bo.solid.fiscal.domain.siat.SiatCredentials;
import bo.solid.fiscal.domain.siat.SiatDateTime;
import bo.solid.fiscal.domain.siat.SiatMessage;
import bo.solid.fiscal.domain.siat.SiatScope;
import bo.solid.fiscal.infrastructure.siat.generated.codigos.RespuestaComunicacion;
import bo.solid.fiscal.infrastructure.siat.generated.codigos.RespuestaCufd;
import bo.solid.fiscal.infrastructure.siat.generated.codigos.RespuestaCuis;
import bo.solid.fiscal.infrastructure.siat.generated.codigos.SolicitudCufd;
import bo.solid.fiscal.infrastructure.siat.generated.codigos.SolicitudCuis;
import bo.solid.fiscal.infrastructure.siat.generated.codigos.ServicioFacturacionCodigos;
import bo.solid.fiscal.infrastructure.siat.generated.sincronizacion.ObjectFactory;
import bo.solid.fiscal.infrastructure.siat.generated.sincronizacion.RespuestaFechaHora;
import bo.solid.fiscal.infrastructure.siat.generated.sincronizacion.ServicioFacturacionSincronizacion;
import bo.solid.fiscal.infrastructure.siat.generated.sincronizacion.SolicitudSincronizacion;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.xml.ws.WebServiceException;
import jakarta.xml.ws.soap.SOAPFaultException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.net.UnknownHostException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import javax.net.ssl.SSLException;
import org.apache.cxf.binding.soap.SoapFault;
import org.apache.cxf.endpoint.Client;
import org.apache.cxf.frontend.ClientProxy;
import org.apache.cxf.jaxws.JaxWsProxyFactoryBean;
import org.apache.cxf.transport.http.HTTPConduit;
import org.apache.cxf.transports.http.configuration.HTTPClientPolicy;
import org.springframework.stereotype.Component;

/**
 * Adaptador SOAP del SIAT (WSDL v2). El token se envia en el header configurado. SIN reintentos propios.
 * Metricas: siat.requests, siat.latency, siat.errors, siat.timeouts (etiquetas operation y outcome).
 */
@Component
class SiatSoapAdapter implements SiatPort {

    private static final int MAX_FAULT_TEXT = 200;

    private final SiatProperties props;
    private final MeterRegistry meters;
    private final Map<String, Object> proxies = new ConcurrentHashMap<>();
    private final ObjectFactory syncFactory = new ObjectFactory();
    private final bo.solid.fiscal.infrastructure.siat.generated.codigos.ObjectFactory codigosFactory =
            new bo.solid.fiscal.infrastructure.siat.generated.codigos.ObjectFactory();

    SiatSoapAdapter(SiatProperties props, MeterRegistry meters) {
        this.props = props;
        this.meters = meters;
    }

    // ------------------------------------------------------------------ operaciones

    @Override
    public CommunicationStatus verifyCommunication(SiatCredentials c) {
        ServicioFacturacionCodigos port = proxy(ServicioFacturacionCodigos.class, "FacturacionCodigos", c.environment());
        RespuestaComunicacion r = call("verificarComunicacion", c, port::verificarComunicacion);
        if (r == null) throw FiscalException.siatProtocol("El SIAT respondio vacio a verificarComunicacion");
        return new CommunicationStatus(Boolean.TRUE.equals(r.isTransaccion()), messages(r.getMensajesList().stream()
                .map(m -> new SiatMessage(m.getCodigo(), m.getDescripcion())).toList()));
    }

    @Override
    public SiatDateTime synchronizeDateTime(SiatCredentials c, SiatScope scope) {
        ServicioFacturacionSincronizacion port =
                proxy(ServicioFacturacionSincronizacion.class, "FacturacionSincronizacion", c.environment());
        SolicitudSincronizacion req = new SolicitudSincronizacion();
        req.setCodigoAmbiente(c.environment().code());
        req.setCodigoSistema(c.systemCode());
        req.setNit(c.nit());
        req.setCodigoSucursal(scope.branchCode());
        req.setCodigoPuntoVenta(syncFactory.createSolicitudSincronizacionCodigoPuntoVenta(scope.posCode()));
        req.setCuis(scope.cuis());
        RespuestaFechaHora r = call("sincronizarFechaHora", c, () -> port.sincronizarFechaHora(req));
        if (r == null) throw FiscalException.siatProtocol("El SIAT respondio vacio a sincronizarFechaHora");
        boolean ok = Boolean.TRUE.equals(r.isTransaccion());
        LocalDateTime time = null;
        if (ok) {
            try {
                time = DateTimeFormatter.ISO_DATE_TIME.parse(r.getFechaHora().strip(), LocalDateTime::from);
            } catch (DateTimeParseException | NullPointerException e) {
                throw FiscalException.siatProtocol("Fecha/hora del SIAT con formato inesperado");
            }
        }
        return new SiatDateTime(ok, time, r.getMensajesList().stream()
                .map(m -> new SiatMessage(m.getCodigo(), m.getDescripcion())).toList());
    }

    @Override
    public CuisResult requestCuis(SiatCredentials c, BranchScope scope) {
        ServicioFacturacionCodigos port = proxy(ServicioFacturacionCodigos.class, "FacturacionCodigos", c.environment());
        SolicitudCuis req = new SolicitudCuis();
        req.setCodigoAmbiente(c.environment().code());
        req.setCodigoModalidad(c.modality().code());
        req.setCodigoSistema(c.systemCode());
        req.setNit(c.nit());
        req.setCodigoSucursal(scope.branchCode());
        req.setCodigoPuntoVenta(codigosFactory.createSolicitudCuisCodigoPuntoVenta(scope.posCode()));
        RespuestaCuis r = call("cuis", c, () -> port.cuis(req));
        if (r == null) throw FiscalException.siatProtocol("El SIAT respondio vacio a cuis");
        boolean ok = Boolean.TRUE.equals(r.isTransaccion());
        return new CuisResult(ok, r.getCodigo(), toInstant(r.getFechaVigencia()), r.getMensajesList().stream()
                .map(m -> new SiatMessage(m.getCodigo(), m.getDescripcion())).toList());
    }

    @Override
    public CufdResult requestCufd(SiatCredentials c, SiatScope scope) {
        ServicioFacturacionCodigos port = proxy(ServicioFacturacionCodigos.class, "FacturacionCodigos", c.environment());
        SolicitudCufd req = new SolicitudCufd();
        req.setCodigoAmbiente(c.environment().code());
        req.setCodigoModalidad(c.modality().code());
        req.setCodigoSistema(c.systemCode());
        req.setNit(c.nit());
        req.setCodigoSucursal(scope.branchCode());
        req.setCodigoPuntoVenta(codigosFactory.createSolicitudCufdCodigoPuntoVenta(scope.posCode()));
        req.setCuis(scope.cuis());
        RespuestaCufd r = call("cufd", c, () -> port.cufd(req));
        if (r == null) throw FiscalException.siatProtocol("El SIAT respondio vacio a cufd");
        boolean ok = Boolean.TRUE.equals(r.isTransaccion());
        return new CufdResult(ok, r.getCodigo(), r.getCodigoControl(), r.getDireccion(), toInstant(r.getFechaVigencia()),
                r.getMensajesList().stream().map(m -> new SiatMessage(m.getCodigo(), m.getDescripcion())).toList());
    }

    /**
     * La doc dice "UTC extendido sin zona". Si el XML trae zona se respeta; si no, se interpreta como hora de Bolivia
     * (America/La_Paz, UTC-4, sin horario de verano). PENDIENTE confirmar contra el piloto real (PilotSmokeIT lo imprime).
     */
    static java.time.Instant toInstant(javax.xml.datatype.XMLGregorianCalendar cal) {
        if (cal == null) return null;
        if (cal.getTimezone() != javax.xml.datatype.DatatypeConstants.FIELD_UNDEFINED) {
            return cal.toGregorianCalendar().toInstant();
        }
        return LocalDateTime.of(cal.getYear(), cal.getMonth(), cal.getDay(), cal.getHour(), cal.getMinute(),
                cal.getSecond(), cal.getMillisecond() == javax.xml.datatype.DatatypeConstants.FIELD_UNDEFINED
                        ? 0 : cal.getMillisecond() * 1_000_000)
                .atZone(java.time.ZoneId.of("America/La_Paz")).toInstant();
    }

    // ------------------------------------------------------------------ infraestructura SOAP

    private static List<SiatMessage> messages(List<SiatMessage> m) { return m; }

    private <T> T call(String operation, SiatCredentials c, Supplier<T> invocation) {
        long t0 = System.nanoTime();
        String outcome = "ok";
        try {
            ApiKeyInterceptor.bind(c.token());
            return invocation.get();
        } catch (WebServiceException w) {
            FiscalException e = classify(w, c.token());
            outcome = outcomeOf(e);
            throw e;
        } finally {
            ApiKeyInterceptor.unbind();
            record(operation, outcome, System.nanoTime() - t0);
        }
    }

    private static String outcomeOf(FiscalException e) {
        return switch (e.code()) {
            case "SIAT_AUTH_001" -> "auth_error";
            case "SIAT_NET_002" -> "timeout";
            case "SIAT_NET_001" -> "unavailable";
            default -> "fault";
        };
    }

    private void record(String operation, String outcome, long nanos) {
        meters.counter("siat.requests", "operation", operation, "outcome", outcome).increment();
        Timer.builder("siat.latency").tag("operation", operation).tag("outcome", outcome).register(meters)
                .record(java.time.Duration.ofNanos(nanos));
        if (!"ok".equals(outcome)) meters.counter("siat.errors", "operation", operation, "outcome", outcome).increment();
        if ("timeout".equals(outcome)) meters.counter("siat.timeouts", "operation", operation).increment();
    }

    /**
     * CXF sin WSDL envuelve todo en WebServiceException: la causa real es SoapFault (falla SOAP del SIAT),
     * HTTPException de CXF (estado HTTP), HttpTimeoutException (timeout) o una IOException de conexion.
     */
    static FiscalException classify(WebServiceException w, String token) {
        for (Throwable t = w; t != null; t = t.getCause()) {
            if (t instanceof SoapFault sf) {
                return fromFaultText(sf.getReason() != null ? sf.getReason() : sf.getMessage(), token);
            }
            if (t instanceof SOAPFaultException sfe) {
                return fromFaultText(sfe.getFault() == null ? "" : sfe.getFault().getFaultString(), token);
            }
            if (t instanceof org.apache.cxf.transport.http.HTTPException h) {
                int s = h.getResponseCode();
                if (s == 401 || s == 403) return FiscalException.siatAuth("El SIAT respondio HTTP " + s);
                if (s >= 500) return FiscalException.siatUnavailable("El SIAT respondio HTTP " + s);
                return FiscalException.siatProtocol("El SIAT respondio HTTP " + s);
            }
            // HttpConnectTimeoutException es subclase de HttpTimeoutException: la conexion nunca se establecio.
            if (t instanceof HttpConnectTimeoutException) {
                return FiscalException.siatUnavailable("No se pudo conectar con el SIAT a tiempo");
            }
            if (t instanceof HttpTimeoutException || t instanceof SocketTimeoutException) {
                return FiscalException.siatTimeout("El SIAT no respondio a tiempo");
            }
            if (t instanceof ConnectException || t instanceof UnknownHostException || t instanceof SSLException) {
                return FiscalException.siatUnavailable("No se pudo conectar con el SIAT");
            }
        }
        return FiscalException.siatUnavailable("Falla de comunicacion con el SIAT");
    }

    static FiscalException fromFaultText(String text, String token) {
        String raw = text == null ? "" : text;
        String norm = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
        if (norm.contains("API KEY")) {
            return FiscalException.siatAuth("El SIAT rechazo el token delegado: " + sanitize(raw, token));
        }
        return FiscalException.siatProtocol("Falla SOAP del SIAT: " + sanitize(raw, token));
    }

    private static String sanitize(String text, String token) {
        String s = text == null ? "" : text;
        if (token != null && !token.isBlank()) s = s.replace(token, "***");
        return s.length() > MAX_FAULT_TEXT ? s.substring(0, MAX_FAULT_TEXT) : s;
    }

    private <T> T proxy(Class<T> sei, String serviceName, Environment env) {
        String base = props.endpoints().get(env == Environment.PRODUCTION ? "production" : "pilot");
        if (base == null || base.isBlank()) {
            throw FiscalException.unprocessable("SIAT_CFG_007", "No hay endpoint SIAT configurado para el ambiente " + env);
        }
        String address = base.replaceAll("/+$", "") + "/" + serviceName;
        Object p = proxies.computeIfAbsent(address, a -> {
            JaxWsProxyFactoryBean f = new JaxWsProxyFactoryBean();
            f.setServiceClass(sei);
            f.setAddress(a);
            f.getOutInterceptors().add(new ApiKeyInterceptor(props.authHeaderName(), props.authHeaderPrefix()));
            Object port = f.create();
            Client client = ClientProxy.getClient(port);
            HTTPConduit conduit = (HTTPConduit) client.getConduit();
            HTTPClientPolicy policy = new HTTPClientPolicy();
            policy.setConnectionTimeout(props.connectTimeoutMs());
            policy.setReceiveTimeout(props.receiveTimeoutMs());
            policy.setAllowChunking(false);
            conduit.setClient(policy);
            return port;
        });
        return sei.cast(p);
    }
}
