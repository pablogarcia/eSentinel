# ADR-0010 — Seguridad, auditoría, idempotencia y outbox (S2)

Estado: Aceptado · 2026-10-09

## Seguridad
- Resource server OAuth2/OIDC con JWT. Claims: `tenant_id` (UUID) y `roles` (lista). Configurables (`solid.fiscal.security.*`). Emisor obligatorio: `OIDC_ISSUER_URI` (si falta, la app no arranca).
- `SUPER_ADMIN` es de **plataforma**: crea tenants, no lee datos de tenants. Cualquier otro rol exige `tenant_id` válido (403 `SECURITY_002/003`).
- RBAC con `@PreAuthorize` en `interfaces`. `application` y `domain` no conocen Spring Security (ArchUnit).
- Un filtro posterior a la validación fija `TenantContext` + `RequestContext` (actor, roles, IP, correlationId, traceId W3C) y los limpia siempre.
- Retirado el filtro `X-Tenant-Id` de S1: ya no existe ningún camino para fijar el tenant sin JWT.
- **Pendiente:** `request.getRemoteAddr()` es la IP del proxy tras el ALB; falta configurar `forward-headers-strategy` con proxies de confianza (S1 infra/AWS).

## Auditoría
- `audit_event`: append-only por privilegios (sin UPDATE/DELETE), por trigger (también bloquea al propietario) y por **cadena de hashes SHA-256 por tenant** (`seq` + `prev_hash` + `hash`), serializada con `pg_advisory_xact_lock` por tenant.
- Se escribe en la **misma transacción** que el cambio. Verificación: `GET /api/v1/audit-events/verification`.
- Límite honesto: un propietario de la BD puede desactivar el trigger y reescribir **toda** la cadena coherentemente. Mitigación prevista: anclar periódicamente el último hash fuera de la BD (S3 Object Lock / WORM) — **pendiente**.
- Partición mensual y retención: pendientes hasta definir plazo legal.

## Idempotencia
- Clave + efecto + respuesta en **una transacción** (`ON CONFLICT DO NOTHING` serializa reintentos simultáneos por el índice único). Mismo payload ⇒ misma respuesta (`Idempotent-Replayed: true`); distinto ⇒ 409; una solicitud fallida no consume la clave.
- Esto cubre efectos locales (PostgreSQL). Para llamadas al SIAT (fuera de TX) se aplica además ADR-0005 (persistir antes de enviar + verificación).
- Registros caducan a las 48 h (`expires_at`); falta el job de limpieza.

## Outbox
- `outbox_event` en la misma TX que el cambio. Relay `OutboxDispatcher`: `FOR UPDATE SKIP LOCKED`, backoff exponencial (tope 300 s), `DEAD` a los 10 intentos. Entrega **at-least-once**; consumidores idempotentes por `event_id`.
- El relay cruza tenants con `SET LOCAL ROLE fiscal_relay_role` (solo SELECT y UPDATE de columnas de estado sobre `outbox_event`). El login de la app es miembro con `INHERIT FALSE, SET TRUE`. **Infra debe replicar esa membresía** (ver `src/test/resources/db/init-roles.sql`). Riesgo aceptado: código de la propia app podría asumir ese rol; su alcance está limitado a esa tabla.
- Kafka: adaptador implementado y probado con `KafkaTemplate` simulado; **no probado contra un broker real** (queda para el sprint de integración con Testcontainers Kafka / MSK).
