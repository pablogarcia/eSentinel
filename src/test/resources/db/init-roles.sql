-- SOLO desarrollo local y tests (Testcontainers / docker-compose). En AWS estos roles los crea la infraestructura.
-- Las contrasenas de este archivo son valores de prueba, no secretos reales.
CREATE ROLE fiscal_app_role NOLOGIN;
CREATE ROLE fiscal_relay_role NOLOGIN;   -- rol del relay del outbox: lee/actualiza outbox de TODOS los tenants (ADR-0010)
CREATE ROLE fiscal_job_role NOLOGIN;     -- lectura entre tenants para jobs de renovacion (ADR-0012)
CREATE ROLE fiscal_app LOGIN PASSWORD 'fiscal_app_dev' NOBYPASSRLS IN ROLE fiscal_app_role;
-- La app puede asumir el rol de relay solo con SET LOCAL ROLE (no hereda sus privilegios).
GRANT fiscal_relay_role TO fiscal_app WITH INHERIT FALSE, SET TRUE;
GRANT fiscal_job_role TO fiscal_app WITH INHERIT FALSE, SET TRUE;
