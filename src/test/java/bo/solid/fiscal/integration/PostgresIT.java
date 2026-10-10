package bo.solid.fiscal.integration;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/** Base: un PostgreSQL real (Testcontainers) compartido por toda la JVM de tests. */
abstract class PostgresIT {

    /** Conexion como propietario (superusuario del contenedor) para sembrar o manipular datos en los tests. */
    static java.sql.Connection owner() throws java.sql.SQLException {
        return java.sql.DriverManager.getConnection(POSTGRES.getJdbcUrl(), "fiscal_owner", "fiscal_owner_test");
    }


    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("solid_fiscal")
            .withUsername("fiscal_owner")
            .withPassword("fiscal_owner_test")
            .withInitScript("db/init-roles.sql");

    /** SIAT simulado compartido por la JVM de tests; cada test lo reprograma con respondWith()/reset(). */
    static final FakeSiatServer SIAT = new FakeSiatServer();

    /** Llave AES-256 SOLO de pruebas (32 bytes en Base64). */
    static final String TEST_KEY_1 = java.util.Base64.getEncoder().encodeToString("test-key-1-0123456789abcdef-0123".getBytes());

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        r.add("spring.datasource.username", () -> "fiscal_app");
        r.add("spring.datasource.password", () -> "fiscal_app_dev");
        r.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        r.add("spring.flyway.user", () -> "fiscal_owner");
        r.add("spring.flyway.password", () -> "fiscal_owner_test");
        // Decodificador JWT perezoso: los tests usan jwt() de spring-security-test y nunca llaman a este emisor.
        r.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> "http://localhost:9/issuer");
        r.add("solid.fiscal.outbox.relay.enabled", () -> "false");
        r.add("solid.fiscal.crypto.active-key-id", () -> "test1");
        r.add("solid.fiscal.crypto.keys.test1", () -> TEST_KEY_1);
        r.add("solid.fiscal.siat.endpoints.pilot", SIAT::baseUrl);
        r.add("solid.fiscal.siat.endpoints.production", () -> "http://127.0.0.1:1/v2");   // puerto cerrado
        r.add("solid.fiscal.siat.connect-timeout-ms", () -> "2000");
        r.add("solid.fiscal.siat.receive-timeout-ms", () -> "1500");   // los tests invocan el dispatcher a mano
    }
}
