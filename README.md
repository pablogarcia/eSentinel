# SOLID Fiscal Bolivia — `fiscal-invoicing-service`

Motor fiscal multi-tenant para facturación electrónica SIAT/SIN (Bolivia). Documentación de diseño en [`docs/solid-fiscal`](docs/solid-fiscal).

## Requisitos
Java 21, Docker (tests de integración con Testcontainers). Maven se descarga con `./mvnw`.

## Comandos
```bash
./mvnw verify                      # unit + arquitectura + integración (PostgreSQL real)
docker compose -f infra/local/docker-compose.yml up -d
DB_PASSWORD=fiscal_app_dev FLYWAY_PASSWORD=fiscal_owner_dev \
  OIDC_ISSUER_URI=https://<tu-emisor-oidc> ./mvnw spring-boot:run
```

## Estructura (hexagonal)
`domain` (sin frameworks) · `application` (casos de uso y puertos) · `infrastructure` (JDBC, tenancy) · `interfaces` (REST). Las reglas se verifican con ArchUnit.

## Multi-tenant
Esquema compartido + `tenant_id` + Row Level Security (ADR-0002). La app usa un rol sin BYPASSRLS y sin propiedad de tablas; cada transacción fija `app.tenant_id` (`TenantAwareTransactionManager`). Sin tenant no se ve ninguna fila.

## Seguridad (S2)
JWT/OIDC con claims `tenant_id` y `roles` (`SUPER_ADMIN`, `TENANT_ADMIN`, `FISCAL_ADMIN`, `ACCOUNTANT`, `SELLER`, `AUDITOR`, `API_CLIENT`). Los `POST` de creación exigen `Idempotency-Key`. Auditoría encadenada por hash (`/api/v1/audit-events`) y outbox transaccional: ver [ADR-0010](docs/solid-fiscal/adr/ADR-0010-seguridad-auditoria-outbox-s2.md).
