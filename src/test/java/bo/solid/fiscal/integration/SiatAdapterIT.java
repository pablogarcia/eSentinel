package bo.solid.fiscal.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.solid.fiscal.application.siat.port.SiatPort;
import bo.solid.fiscal.domain.org.Environment;
import bo.solid.fiscal.domain.org.Modality;
import bo.solid.fiscal.domain.shared.FiscalException;
import bo.solid.fiscal.domain.siat.CommunicationStatus;
import bo.solid.fiscal.domain.siat.SiatCredentials;
import bo.solid.fiscal.domain.siat.SiatDateTime;
import bo.solid.fiscal.domain.siat.SiatScope;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.LocalDateTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Contrato del adaptador SOAP contra un SIAT simulado: headers, parseo, clasificacion de fallas y metricas. */
@SpringBootTest
class SiatAdapterIT extends PostgresIT {

    static final String TOKEN = "SECRET-TOKEN-123";

    @Autowired SiatPort siat;
    @Autowired MeterRegistry meters;

    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void setUp() {
        SIAT.reset();
        logs.start();
        ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).addAppender(logs);
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME)).detachAppender(logs);
    }

    private static SiatCredentials creds(Environment env) {
        return new SiatCredentials(env, 1003579028L, "SYS-1", Modality.ELECTRONIC_ONLINE, TOKEN);
    }

    @Test
    void verify_communication_sends_the_token_in_the_apikey_header_and_parses_the_answer() {
        CommunicationStatus s = siat.verifyCommunication(creds(Environment.PILOT));

        assertThat(s.ok()).isTrue();
        assertThat(s.messages()).hasSize(1);
        assertThat(s.messages().get(0).description()).isEqualTo("OK simulado");
        FakeSiatServer.Request req = SIAT.last();
        assertThat(req.path()).isEqualTo("/v2/FacturacionCodigos");
        assertThat(req.header("apikey")).isEqualTo("TokenApi " + TOKEN);
        assertThat(req.header("Authorization")).isNull();
        assertThat(req.body()).contains("verificarComunicacion").doesNotContain(TOKEN);   // el token no va en el sobre SOAP
    }

    @Test
    void synchronize_date_time_sends_the_scope_and_parses_the_server_clock() {
        SIAT.respondWith(r -> FakeSiatServer.Response.soap(FakeSiatServer.fechaHora("2026-10-09T21:30:15.123")));

        SiatDateTime t = siat.synchronizeDateTime(creds(Environment.PILOT), new SiatScope(0, 0, "CUIS-ABC"));

        assertThat(t.ok()).isTrue();
        assertThat(t.serverTime()).isEqualTo(LocalDateTime.of(2026, 10, 9, 21, 30, 15, 123_000_000));
        String body = SIAT.last().body();
        assertThat(SIAT.last().path()).isEqualTo("/v2/FacturacionSincronizacion");
        assertThat(body).contains("<codigoAmbiente>2</codigoAmbiente>").contains("<codigoSistema>SYS-1</codigoSistema>")
                .contains("<codigoSucursal>0</codigoSucursal>").contains("<cuis>CUIS-ABC</cuis>")
                .contains("<nit>1003579028</nit>").contains("<codigoPuntoVenta>0</codigoPuntoVenta>");
    }

    @Test
    void unparseable_server_clock_is_a_protocol_error() {
        SIAT.respondWith(r -> FakeSiatServer.Response.soap(FakeSiatServer.fechaHora("ayer por la tarde")));
        assertThatThrownBy(() -> siat.synchronizeDateTime(creds(Environment.PILOT), new SiatScope(0, 0, "C")))
                .isInstanceOfSatisfying(FiscalException.class, e -> assertThat(e.code()).isEqualTo("SIAT_PROTO_001"));
    }

    @Test
    void api_key_fault_is_an_auth_error_and_never_echoes_the_token() {
        SIAT.respondWith(r -> FakeSiatServer.Response.fault("API KEY NO VALIDO " + TOKEN));
        assertThatThrownBy(() -> siat.verifyCommunication(creds(Environment.PILOT)))
                .isInstanceOfSatisfying(FiscalException.class, e -> {
                    assertThat(e.code()).isEqualTo("SIAT_AUTH_001");
                    assertThat(e.getMessage()).doesNotContain(TOKEN).contains("***");
                });
    }

    @Test
    void missing_api_key_fault_with_accents_is_also_auth() {
        SIAT.respondWith(r -> FakeSiatServer.Response.fault("EL SERVICIO REQUIERE API KEY"));
        assertThatThrownBy(() -> siat.verifyCommunication(creds(Environment.PILOT)))
                .isInstanceOfSatisfying(FiscalException.class, e -> assertThat(e.code()).isEqualTo("SIAT_AUTH_001"));
    }

    @Test
    void other_soap_faults_are_protocol_errors() {
        SIAT.respondWith(r -> FakeSiatServer.Response.fault("Error interno raro"));
        assertThatThrownBy(() -> siat.verifyCommunication(creds(Environment.PILOT)))
                .isInstanceOfSatisfying(FiscalException.class, e -> assertThat(e.code()).isEqualTo("SIAT_PROTO_001"));
    }

    @Test
    void gateway_5xx_is_unavailable() {
        SIAT.respondWith(r -> FakeSiatServer.Response.http(503));
        assertThatThrownBy(() -> siat.verifyCommunication(creds(Environment.PILOT)))
                .isInstanceOfSatisfying(FiscalException.class, e -> {
                    assertThat(e.kind()).isEqualTo(FiscalException.Kind.UPSTREAM_UNAVAILABLE);
                    assertThat(e.code()).isEqualTo("SIAT_NET_001");
                });
    }

    @Test
    void slow_response_is_a_timeout_and_is_never_retried_by_the_adapter() {
        SIAT.respondWith(r -> FakeSiatServer.Response.soap(FakeSiatServer.COMM_OK).delayed(4000));
        assertThatThrownBy(() -> siat.verifyCommunication(creds(Environment.PILOT)))
                .isInstanceOfSatisfying(FiscalException.class, e -> {
                    assertThat(e.kind()).isEqualTo(FiscalException.Kind.UPSTREAM_TIMEOUT);
                    assertThat(e.code()).isEqualTo("SIAT_NET_002");
                });
        assertThat(SIAT.requests).hasSize(1);   // un solo intento
    }

    @Test
    void closed_port_is_unavailable() {
        // El ambiente PRODUCTION apunta a un puerto cerrado en los tests.
        assertThatThrownBy(() -> siat.verifyCommunication(creds(Environment.PRODUCTION)))
                .isInstanceOfSatisfying(FiscalException.class, e -> assertThat(e.code()).isEqualTo("SIAT_NET_001"));
    }

    @Test
    void metrics_count_requests_errors_and_timeouts() {
        double okBefore = meters.counter("siat.requests", "operation", "verificarComunicacion", "outcome", "ok").count();
        siat.verifyCommunication(creds(Environment.PILOT));
        assertThat(meters.counter("siat.requests", "operation", "verificarComunicacion", "outcome", "ok").count())
                .isEqualTo(okBefore + 1);

        double toBefore = meters.counter("siat.timeouts", "operation", "verificarComunicacion").count();
        SIAT.respondWith(r -> FakeSiatServer.Response.soap(FakeSiatServer.COMM_OK).delayed(4000));
        assertThatThrownBy(() -> siat.verifyCommunication(creds(Environment.PILOT))).isInstanceOf(FiscalException.class);
        assertThat(meters.counter("siat.timeouts", "operation", "verificarComunicacion").count()).isEqualTo(toBefore + 1);
        assertThat(meters.find("siat.latency").timers()).isNotEmpty();
    }

    @Test
    void the_token_never_appears_in_any_log_line_even_on_failures() {
        siat.verifyCommunication(creds(Environment.PILOT));
        SIAT.respondWith(r -> FakeSiatServer.Response.fault("API KEY NO VALIDO " + TOKEN));
        assertThatThrownBy(() -> siat.verifyCommunication(creds(Environment.PILOT))).isInstanceOf(FiscalException.class);
        SIAT.respondWith(r -> FakeSiatServer.Response.http(503));
        assertThatThrownBy(() -> siat.verifyCommunication(creds(Environment.PILOT))).isInstanceOf(FiscalException.class);
        assertThat(creds(Environment.PILOT).toString()).doesNotContain(TOKEN);

        for (ILoggingEvent e : logs.list) {
            assertThat(e.getFormattedMessage()).doesNotContain(TOKEN);
            if (e.getThrowableProxy() != null) {
                assertThat(ch.qos.logback.classic.spi.ThrowableProxyUtil.asString(e.getThrowableProxy())).doesNotContain(TOKEN);
            }
        }
    }
}
