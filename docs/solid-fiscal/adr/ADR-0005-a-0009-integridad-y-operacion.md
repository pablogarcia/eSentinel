# ADRs de integridad y operación (S0)

---

## ADR-0005 — Persistir antes de enviar; idempotencia; reconciliación antes que reintento
- **Decisión:**
  1. `Idempotency-Key` obligatoria en `POST` mutantes; `idempotency_record(tenant, key, request_hash)` UNIQUE. Misma clave + mismo hash → mismo resultado; distinto hash → 409.
  2. En una sola transacción (TX1): idempotencia → número → CUFD → fecha/hora de emisión fijada **una vez** → CUF → XML → XSD → firma → gzip → SHA-256 → `fiscal_document` + `fiscal_document_xml` en estado `READY_TO_SEND`. Recién después se llama a SIAT, **fuera de transacción**.
  3. Timeout o corte → `SENT_UNKNOWN`; un worker consulta `verificacionEstadoFactura` por CUF antes de cualquier reenvío. El reenvío usa el mismo archivo (mismo CUF y hash).
  4. Backoff exponencial solo en operaciones consultivas; el envío solo se reintenta tras verificación.
- **Pendiente de probar en piloto:** respuesta del SIAT ante reenvío del mismo CUF (prueba P-01).
- **Fuente:** práctica recomendada en [Emisión y Envío](https://siatinfo.impuestos.gob.bo/index.php/facturacion-en-linea/emision-y-envio-de-facturas/emision-y-envio) (registrar facturas sin código de respuesta y verificarlas).

## ADR-0006 — Numeración con tabla `fiscal_sequence` + `SELECT … FOR UPDATE`
- **Decisión:** una fila por (tenant, company, branch, pos, document_sector); incremento dentro de TX1. Se descartan `SEQUENCE` (huecos por rollback, no reversible) y optimistic locking (reintentos bajo contención).
- **Pendiente:** granularidad real exigida por el SIN (P-02). Hasta saberlo, la clave incluye pos y sector, y se puede colapsar sin migrar datos.
- **Consecuencias:** serialización por POS; adecuado a la capacidad objetivo. Si un POS necesita más, asignación por bloques con reserva auditada.

## ADR-0007 — Códigos de acceso: CUIS/CUFD con lock en PostgreSQL
- **Decisión:** `pg_advisory_xact_lock(hashtextextended(branch||pos))` → re-leer estado → solo entonces pedir al SIAT. `UNIQUE` parcial "una fila ACTIVE por (branch,pos)". Redis solo cachea. Jobs: renovar CUFD antes de vencer (ventana configurable) y CUIS desde T-5 días ([regla oficial](https://siatinfo.impuestos.gob.bo/index.php/facturacion-en-linea/implementacion-servicios-facturacion/codigos/solicitud-cuis)); tras renovar CUIS, pedir CUFD nuevo. Sincronizar fecha/hora antes de cada CUFD.
- **Consecuencia:** se conserva histórico de CUFD (necesario para offline y paquetes).

## ADR-0008 — Secretos, tokens y certificados
- **Decisión:** token delegado y .p12 cifrados con envelope encryption (clave de datos por tenant, CMK en AWS KMS); referencias en BD, material en Secrets Manager. La clave privada se carga en memoria solo durante la firma y no se registra en logs. Rotación: token por expiración (lo fija el contribuyente al generarlo), certificado alerta 60/30/7 días. Revocación: consumir `notificaCertificadoRevocado` cuando corresponda **[PENDIENTE]** su uso exacto.
- **Local/dev:** `docker-compose` con LocalStack/Vault dev; nunca secretos en Git (hook pre-commit con gitleaks).

## ADR-0009 — Outbox, Kafka y Redis
- **Decisión:** PostgreSQL es la fuente de verdad. `outbox_event` se inserta en la misma transacción que el cambio; relay con `FOR UPDATE SKIP LOCKED` (poller en MVP; Debezium si crece). Consumidores idempotentes por `event_id`. Kafka transporta referencias, no XML firmados. Redis: caché de CUFD vigente, catálogos, configuración, rate limit; jamás fuente única de numeración, idempotencia, estado o tokens.
- **Eventos:** `InvoiceCreated`, `InvoiceSigned`, `InvoiceSubmitted`, `InvoiceAccepted`, `InvoiceObserved`, `InvoiceCancelled`, `ContingencyStarted`, `PackageCreated`, `PackageSubmitted`, `PackageAccepted`.
