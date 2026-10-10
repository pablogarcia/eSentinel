package bo.solid.fiscal.infrastructure.siat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import bo.solid.fiscal.domain.org.Environment;
import bo.solid.fiscal.domain.org.Modality;
import bo.solid.fiscal.domain.shared.FiscalException;
import bo.solid.fiscal.domain.siat.SiatCredentials;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

/**
 * Contrato con el SIAT. Offline: los WSDL versionados no se tocaron (MANIFEST.sha256).
 * Online (opt-in, -Dsiat.live=true): los WSDL del piloto siguen siendo identicos a los versionados y el adaptador
 * habla correctamente con el servicio REAL (con un token invalido a proposito: debe responder "auth error").
 */
class SiatContractTest {

    private static final String BASE = "https://pilotosiatservicios.impuestos.gob.bo/v2";
    private static final String DIR = "/siat/wsdl/v2/";

    private static String resource(String name) throws Exception {
        try (InputStream in = SiatContractTest.class.getResourceAsStream(DIR + name)) {
            assertThat(in).as("recurso %s", name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static String sha256(String s) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void versioned_wsdls_match_their_manifest() throws Exception {
        for (String line : resource("MANIFEST.sha256").split("\n")) {
            if (line.isBlank()) continue;
            String[] p = line.trim().split("\\s+");
            assertThat(sha256(resource(p[1]))).as("hash de %s", p[1]).isEqualTo(p[0]);
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "siat.live", matches = "true")
    void live_pilot_wsdls_are_identical_to_the_versioned_copies() throws Exception {
        HttpClient http = HttpClient.newHttpClient();
        for (String svc : new String[]{"FacturacionCodigos", "FacturacionSincronizacion", "FacturacionOperaciones"}) {
            String liveAbstract = http.send(HttpRequest.newBuilder(URI.create(BASE + "/" + svc + "?wsdl=Servicio" + svc + ".wsdl")).build(),
                    HttpResponse.BodyHandlers.ofString()).body();
            assertThat(sha256(liveAbstract)).as("WSDL abstracto de %s cambio en el SIAT", svc)
                    .isEqualTo(sha256(resource(svc + ".abstract.wsdl")));
        }
    }

    @Test
    @EnabledIfSystemProperty(named = "siat.live", matches = "true")
    void live_pilot_rejects_an_invalid_token_with_the_expected_auth_error() {
        SiatSoapAdapter adapter = new SiatSoapAdapter(new SiatProperties(Map.of("pilot", BASE), 5_000, 15_000, null, null),
                new SimpleMeterRegistry());
        SiatCredentials bad = new SiatCredentials(Environment.PILOT, 1L, "X", Modality.ELECTRONIC_ONLINE, "TOKEN-INVALIDO-DE-PRUEBA");
        assertThatThrownBy(() -> adapter.verifyCommunication(bad))
                .isInstanceOfSatisfying(FiscalException.class, e -> assertThat(e.code()).isEqualTo("SIAT_AUTH_001"));
    }
}
