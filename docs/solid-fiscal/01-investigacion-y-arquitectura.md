# SOLID FISCAL BOLIVIA — Investigación SIAT y propuesta de arquitectura (v0.1)

Fecha de investigación: 2026-10-09. Estado: **borrador para aprobación. No hay código.**

Leyenda de certeza (regla 51): **[CONFIRMADO]** leído en fuente oficial en esta sesión · **[NO CONFIRMADO]** no pude verificarlo · **[PENDIENTE]** requiere decisión o validación con el SIN · **[FUTURO]** documentación aún en construcción · **[INFERENCIA]** deducción mía, no del SIN.

---

## A. Resumen ejecutivo

1. **La ventana de autorización está abierta ahora.** La RND 102600000034 (01/09/2026) fija que los sistemas propios y de proveedor hagan pruebas en piloto y se autoricen **del 01/oct al 16/nov/2026**. Hoy es 09/oct: quedan ~5 semanas. Un sistema nuevo no llegará a autorizarse en esa ventana. [CONFIRMADO]
2. **Esa ventana es sobre el "Anexo Técnico Ley 1733 (v0.0, en construcción)"**, es decir el esquema IVA Transparente. Ese anexo, hoy, solo tiene el índice (8 bloques) y ningún XSD/servicio publicado. [CONFIRMADO / FUTURO]
3. **Mientras no entre en vigencia la RND que implemente el cambio, rige la RND 102100000011 (2021) con su modelo SOAP v2 actual.** (Disposición Adicional Única de la RND 34/2026.) [CONFIRMADO]
4. **La reforma de IVA (Art. 5 Ley 843, modificado por Ley 1733) entra en vigencia el primer día del mes siguiente a la publicación de su Decreto Supremo reglamentario.** No encontré ese DS. [CONFIRMADO la regla; PENDIENTE la fecha]
5. **Consecuencia de diseño:** hay que construir contra el esquema vigente (v2, XSD 2021) y aislar todo lo fiscal tras versiones (`FiscalRuleVersion`, `XmlSchemaVersion`, `SiatApiVersion`, `TaxCalculationVersion`) para que el cambio IVA Transparente sea un módulo nuevo, no una reescritura.
6. **Hallazgo que condiciona el modelo SaaS:** el Token Delegado lo genera **cada contribuyente** en el portal SIAT y se lo entrega al proveedor; y la fase 3 exige asociar el sistema con cada cliente. Así que SOLID Fiscal no puede "emitir por todos" sin onboarding por tenant. [CONFIRMADO]

---

## B. Normativa vigente — fuentes y estado

| Documento | Estado | Fuente |
|---|---|---|
| RND 102100000011 (11/08/2021) Sistema de Facturación | **Vigente** (base) | [siatinfo – Base legal](https://siatinfo.impuestos.gob.bo/index.php/informacion/base-legal) · [RND11.pdf](https://www.impuestos.gob.bo/wp-content/uploads/2025/10/RND11.pdf) |
| Modificatorias RND 17/2021, 21/2021, 23/2023-034… | Vigentes (lista en Base legal; no leí su texto) | idem |
| RND 101800000026 (sistemas proveedor certificados) | Citada por fuente secundaria, no leí el PDF [NO CONFIRMADO] | [PDF](https://www.impuestos.gob.bo/wp-content/uploads/2025/10/RND-101800000026.pdf) |
| **RND 102600000034 (01/09/2026) Ajustes al Sistema de Facturación** | Vigente; abre anexo y plazo de pruebas/autorización | [PDF oficial](https://www.impuestos.gob.bo/wp-content/uploads/2026/09/RND-1026000000034.pdf) (leído) |
| Ley 1733 (27/05/2026) | Vigente; Disp. Adicional 2ª (Art. 5 Ley 843) diferida a DS | [PDF](https://www.impuestos.gob.bo/wp-content/uploads/2026/05/L17332051NCPP.pdf) (no leí el texto completo) |
| Anexo Técnico Ley 1733 v0.0 | **En construcción** | [siatanexo](https://siatanexo.impuestos.gob.bo/index.php?option=com_content&view=article&id=255) (leído: solo índice) |
| RND 102600000025 (13/07/2026) anulación posterior a DDJJ | Solo vista en un resumen secundario [NO CONFIRMADO] | [boliviaimpuestos.com](https://boliviaimpuestos.com/rnd-102600000034-ajustes-facturacion-iva-transparente/) |

Fuente técnica principal: [siatinfo.impuestos.gob.bo](https://siatinfo.impuestos.gob.bo/) (Anexo Técnico vigente). Nota: `WebFetch` falló en esta sesión; leí las páginas con `curl` y descargué solo los WSDL, el XSD de Compra-Venta y la RND 34 a carpetas temporales.

## C. Cambios 2026 identificados

| Fecha | Cambio | Impacto en el diseño | Estado |
|---|---|---|---|
| 27/05/2026 | Ley 1733 (IVA Transparente: separar IVA del neto) | Cambia cálculo de totales y XML | Vigente la ley; efecto técnico [FUTURO] |
| 01/09/2026 | RND 34/2026 | Ventana pruebas/autorización 01/oct–16/nov; consulta pública (sin fecha oficial en la RND; una fuente secundaria dice hasta 21/09) | [CONFIRMADO] |
| 11/04/2026 | Versión 1.0.56: crédito fiscal del Doc. Sector 55 al 100% | Regla de validación por sector | [CONFIRMADO] ([Versionamiento 2026](https://siatinfo.impuestos.gob.bo/index.php/versionamiento/versionamiento-2026)) |
| 13/05/2026 | v1.0.57: Doc. Sector 55 pasa a "Factura de Comercialización de Combustible"; XML/XSD sin cambio | Los nombres cambian, los códigos no: **no hardcodear nombres** | [CONFIRMADO] |
| 10/06/2026 | v1.0.58: leyenda "Tarifa Dignidad" → "Descuento Patria" en sectores Servicios Básicos | Textos de PDF deben venir de catálogo/config versionada | [CONFIRMADO] |

**No encontré** en lo que leí: cambios de SOAP, nuevos WSDL, ni reglas nuevas de moneda extranjera en 2026. El sector 9 (Moneda Extranjera) sigue con XSD 2021. [NO CONFIRMADO que no existan; sí confirmado que no están publicados en las páginas que revisé.]

## D. Requisitos SIAT para un sistema (propio/proveedor)

Componentes mínimos ([Sistema Informático](https://siatinfo.impuestos.gob.bo/index.php/facturacion-en-linea/requerimientos/sistema-informatico)) [CONFIRMADO]:
a) Emisor (individual, contingencia, masivo opcional) · b) Gestor (anulación, etc.) · c) Sincronización de catálogos **diaria** · d) Sincronización fecha/hora **diaria obligatoria** · e) Registro de eventos significativos · f) Envío de XML y representación gráfica (impresión no obligatoria).

Conexión: HTTPS; internet mínimo 1 Mbps urbano / 512 Kbps rural; opciones fibra o MPLS coordinadas antes de producción ([Esquemas de conexión](https://siatinfo.impuestos.gob.bo/index.php/facturacion-en-linea/requerimientos/esquemas-de-conexion)).

**Requisitos para solicitar autorización** ([Proceso](https://siatinfo.impuestos.gob.bo/index.php/facturacion-en-linea/autorizacion-de-sistemas/proceso-de-autorizacion)) [CONFIRMADO]: NIT activo, obligación IVA, sin marcas de control/contravenciones. Se registra en el portal con SIAT en Línea: nombre comercial, **tipo (propio/proveedor)**, versión, marca de proceso masivo, modalidad, **tipos de documento sector** a ofertar, contactos. Resultado: **código de sistema** + parámetros + URLs de prueba.

Reglas importantes: sistemas **Web y API se autorizan por separado**; el proveedor debe implementar **todas** las funcionalidades mínimas; para modalidad Electrónica hace falta certificado digital de prueba pedido al SIN vía CSR (formato AGETIC).

### Fases de autorización [CONFIRMADO]
- **Fase 1 – Pruebas** ([detalle](https://siatinfo.impuestos.gob.bo/index.php/facturacion-en-linea/autorizacion-de-sistemas/pruebas-para-la-autorizacion-del-sistema-de-facturacion/fase-i-pruebas)), con casos Excel del SIN y mínimos: I CUIS (2 por caso) · II catálogos (50) · III CUFD (100) · IV emisión individual (**500 por sector**) · V eventos significativos · … · anulaciones (**250**) · firma digital (**250**) · emisión masiva (10 pruebas, paquetes de hasta 2000 en la prueba) · masiva IEHD (hasta 1000). No leí las etapas VI y XI+ completas.
- **Fase 2 – Inspección** ([detalle](https://siatinfo.impuestos.gob.bo/index.php/facturacion-en-linea/autorizacion-de-sistemas/pruebas-para-la-autorizacion-del-sistema-de-facturacion/fase-ii-inspeccion)): nominatividad (documento de identidad siempre), no facturas en 0 salvo Gift Card, múltiples medios de pago, segunda leyenda aleatoria (Ley 453), tercera leyenda en línea/fuera de línea, enmascarar tarjeta, moneda extranjera usa tipo de cambio en precio unitario, paquetes offline en **48 h**, paquetes de manuales transcritos en **72 h**, anulación en plazo (exportación **180 días**), control CAFC, XML+PDF al comprador, entrada/salida de contingencia y envío **automáticos**.
- **Fase 3 – Piloto de proveedor** ([detalle](https://siatinfo.impuestos.gob.bo/index.php/facturacion-en-linea/autorizacion-de-sistemas/pruebas-para-la-autorizacion-del-sistema-de-facturacion/fase-iii-pruebas-piloto)): asociar sistema↔cliente, el cliente confirma y genera token delegado, se prueba catálogos, emisión, anulación, **reversión de anulación**, eventos, offline automático, manuales, masivo. Pruebas de carga son **responsabilidad del proveedor, no medidas por el SIN**. Iniciar operaciones equivale a **declaración jurada**.

## E. Tipos de documento

- Tipos: factura con derecho a crédito fiscal (1), sin derecho (2), **documento de ajuste** (3 = Nota Crédito-Débito, hasta 18 meses después de la original), documento equivalente. [CONFIRMADO] ([Tipos](https://siatinfo.impuestos.gob.bo/index.php/informacion/tipos-facturas))
- **Documentos sector**: hay ≥ 24 con XML/XSD propio (Compra-Venta=1, Alquiler=2, Exportación=3, … Moneda Extranjera=9, Servicios Básicos=13, … Nota C/D=24, combustibles=55, etc.). El menú lista ~30 sectores. **El MVP solo cubre sector 1 + sector 24.**
- Modalidades: Electrónica en Línea (1, con XMLDSig), Computarizada en Línea (2, sin firma, usa SHA-256 como huella), Portal Web en Línea (3). **[PENDIENTE decisión: ver sección AL, D-02]**

## F. Arquitectura propuesta

Tu diagrama es correcto en la idea; cambios que recomiendo:

1. **Separar "motor fiscal" de "adaptador SIAT" con un puerto** (`SiatPort`) — ya lo pedías; lo mantengo.
2. **Un `Fiscal Document` persistido ANTES de hablar con SIAT** (XML firmado + CUF + hash congelados). Es lo que hace posible reintentar sin duplicar (ver §Z).
3. **Document Engine no es un componente de runtime aparte**: es un módulo (XML/PDF) dentro del mismo servicio.
4. **Workers de SIAT por Kafka** solo para lo asíncrono (paquetes, reintentos, reconciliación). La emisión online individual debe ser **síncrona con timeout acotado** y, si no concluye, devolver 202 con estado consultable.
5. **Un solo despliegue (`fiscal-invoicing-service`)** con módulos hexagonales; el worker es el mismo artefacto con otro perfil Spring.

```text
SOLID ERP ──REST(Idempotency-Key)──► [interfaces] Fiscal API
                                          │
                                   [application] casos de uso (IssueInvoice, Cancel, Reconcile…)
                                          │
                  ┌───────────────────────┼────────────────────────┐
              [domain]                 [domain]                 [domain]
          Fiscal Engine            Document model           Ports: SiatPort, CertificatePort,
          (CUF, reglas, tax)       (Invoice, NCD…)          RepositoryPorts, EventPublisher
                                          │
                              [infrastructure]
        PostgreSQL(+Flyway,RLS) · Redis(cache/locks) · Kafka(outbox relay) · SiatSoapAdapter(v2)
        XmlBuilder/XSD · XmlDSig · KMS/Vault · PDF renderer
```

Tecnologías: Java 21, Spring Boot 3.x, Maven, PostgreSQL, Redis, Kafka, JAX-WS/CXF (cliente SOAP generado desde WSDL), Santuario/JDK XMLDSig, OpenPDF o iText… (ver AF), Micrometer + OTel.

## G. Multi-tenant

| Opción | Pros | Contras |
|---|---|---|
| Shared DB / shared schema + `tenant_id` + **Row Level Security** | Simple, migraciones únicas, barato, escala a cientos de tenants | Riesgo de fuga si falta el filtro (mitigado con RLS) |
| Schema por tenant | Aislamiento más fuerte | Flyway ×N, pool y migraciones costosas |
| DB por tenant | Máximo aislamiento | Costo operativo alto, innecesario al inicio |

**Recomendación:** shared schema + `tenant_id` en toda tabla + **RLS** de PostgreSQL con `SET LOCAL app.tenant_id` por transacción, y claves únicas siempre compuestas con `tenant_id`. Dejar la puerta abierta a "DB por tenant" para clientes grandes (el `tenant_id` ya lo permite). Prioridad: integridad y aislamiento > simplicidad.

Jerarquía: `Tenant → Company(Taxpayer/NIT) → Branch(codigoSucursal 0..n) → PointOfSale(codigoPuntoVenta)`. Un tenant puede tener varios NIT. Token, CUIS, CUFD, certificado y numeración cuelgan de **NIT + sucursal + POS**.

## H. Modelo de dominio

```text
Commercial Model (ERP DTO)  ──mapper──►  Fiscal Domain Model  ──mapper (por XmlSchemaVersion)──►  SIAT XML Model ──► XML
```

- `FiscalDocument` (agregado raíz): id, tenant, company, branch, pos, `documentSector`, `documentKind` (INVOICE | ADJUSTMENT), `emissionType` (ONLINE/OFFLINE/MASSIVE), `status`, número, `cuf`, `cufdId`, `cuisId`, versiones (`fiscalRuleVersion`, `xmlSchemaVersion`, `siatApiVersion`, `taxCalcVersion`, `catalogVersionSet`), `idempotencyKey`, hash del payload comercial.
- Subtipos por composición: `Invoice` (+ `InvoiceItem`), `CreditDebitNote` (+ referencia a original: CUF, número, fecha, monto), `OtherFiscalDocument`.
- **`MonetaryBreakdown`** (value object): `currency`, `exchangeRate`, `exchangeRateDate`, `exchangeRateSource`, `amountForeign`, `amountLocal`, `roundingMode=HALF_UP, scale=2` ([redondeo oficial](https://siatinfo.impuestos.gob.bo/index.php/facturacion-en-linea/algoritmos-utilizados/algoritmo-de-redondeo)), más por línea: cantidad, precio, descuento, subtotal. **Guardar entradas y resultados de cada cálculo**, no solo el total.
- Los totales fiscales los calcula `TaxCalculator` versionado (`TaxCalculationVersion`), así IVA Transparente es otra implementación, no un `if`.

## I. Modelo de base de datos inicial

Convenciones: PK `uuid` (v7), `tenant_id uuid NOT NULL` en todas, `created_at/updated_at timestamptz`, `version bigint` (optimistic lock), RLS por `tenant_id`. Dinero `numeric(20,2)` (SIAT: hasta 20 enteros y 2 decimales en tipo de cambio oficial). Tablas fiscales inmutables = solo INSERT.

| Tabla | Campos clave | Constraints / índices |
|---|---|---|
| `tenant` | id, name, status | PK |
| `company` | id, tenant_id, nit `bigint`, razon_social, environment(1/2), modality(1/2/3), system_code | UNIQUE(tenant_id,nit,environment) |
| `branch` | company_id, codigo_sucursal int, direccion, municipio, telefono | UNIQUE(company_id,codigo_sucursal) |
| `point_of_sale` | branch_id, codigo_punto_venta int, tipo_punto_venta | UNIQUE(branch_id,codigo_punto_venta) |
| `fiscal_configuration` | company_id, ambiente, modalidad, versiones por defecto, flags | UNIQUE(company_id,ambiente) |
| `fiscal_token` | company_id, token_ciphertext, kms_key_ref, expires_at, status | índice (company_id,status) — **token nunca en claro** |
| `fiscal_certificate` | company_id, subject, serial, not_before/after, secret_ref (KMS/Vault), status, revoked_at | UNIQUE(company_id,serial) — **sin private key en BD** |
| `cuis` | branch_id, pos_id nullable, codigo, fecha_vigencia, status, requested_at | UNIQUE parcial (branch_id,pos_id) WHERE status='ACTIVE' |
| `cufd` | cuis_id, codigo, codigo_control, direccion, fecha_vigencia, status | UNIQUE(cuis_id,codigo); UNIQUE parcial una ACTIVE por (branch,pos); histórico conservado |
| `fiscal_sequence` | company_id, branch_id, pos_id, document_sector, last_number bigint | UNIQUE de la combinación; ver §14 |
| `fiscal_document` | ver H + `emission_type`, `status`, `number`, `cuf`, `reception_code`, `siat_state_code`, `idempotency_key`, `request_hash` | **UNIQUE(tenant_id,idempotency_key)**; **UNIQUE(company_id,cuf)**; UNIQUE(company_id,branch,pos,sector,number) |
| `invoice_item` | document_id, line_no, codigo_producto_sin, codigo_producto, descripcion, cantidad, unidad_medida, precio_unitario, descuento, subtotal | PK(document_id,line_no) |
| `credit_debit_note` | document_id, original_document_id, original_cuf, original_number, original_date, reason | FK a original |
| `fiscal_document_xml` | document_id, kind(UNSIGNED/SIGNED/GZIP), content bytea/ref S3, sha256, schema_version | Solo INSERT; UNIQUE(document_id,kind) |
| `fiscal_document_event` | document_id, from_state, to_state, actor, reason, siat_response_id, at | Solo INSERT, partición mensual |
| `siat_transaction` | tenant, operation, request_id, document_id, started_at, finished_at, outcome(OK/ERROR/TIMEOUT/UNKNOWN), correlation_id | índice (document_id), (outcome) |
| `siat_response` | transaction_id, raw_request, raw_response (redactado), codigo_estado, codigo_recepcion, errors jsonb | Solo INSERT |
| `contingency_event` | branch_id, pos_id, codigo_evento(1-7), cufd_evento, inicio, fin, siat_reception_code, status | índice (branch,status) |
| `package` | contingency_event_id, kind(OFFLINE/MASSIVE/MANUAL), count(≤500/1000), gzip_sha256, reception_code, state | CHECK(count ≤ límite) |
| `package_item` | package_id, document_id | UNIQUE(document_id) |
| `fiscal_catalog` / `fiscal_catalog_version` / `fiscal_catalog_item` | catalogo, scope, fetched_at, hash; items jsonb | UNIQUE(catalog,scope,hash) |
| `outbox_event` | aggregate, type, payload jsonb, created_at, published_at | índice parcial WHERE published_at IS NULL |
| `audit_event` | who, when, tenant, document, operation, old/new state, ip, correlation_id, trace_id, prev_hash, hash | Solo INSERT, **cadena de hashes**, REVOKE UPDATE/DELETE |
| `idempotency_record` | tenant, key, request_hash, response_snapshot, status | UNIQUE(tenant,key) |

Particionamiento: `fiscal_document_event`, `siat_response`, `audit_event` por mes. Retención: documentos fiscales y evidencia **[PENDIENTE: confirmar plazo legal de conservación]**; propuesta mínima 10 años hasta confirmarlo.

(El DDL Flyway se escribe después de aprobar este modelo.)

## J. Diagrama ER (resumen)

```text
Tenant 1─* Company 1─* Branch 1─* PointOfSale
Company 1─* FiscalToken | FiscalCertificate | FiscalConfiguration
Branch/POS 1─* CUIS 1─* CUFD
Company/Branch/POS/Sector 1─1 FiscalSequence
Company 1─* FiscalDocument ─* InvoiceItem | ─1 CreditDebitNote ─> FiscalDocument(original)
FiscalDocument 1─* FiscalDocumentXml | FiscalDocumentEvent | SIATTransaction 1─* SIATResponse
ContingencyEvent 1─* Package 1─* PackageItem ─> FiscalDocument
FiscalCatalog 1─* FiscalCatalogVersion 1─* FiscalCatalogItem
OutboxEvent, AuditEvent (transversales)
```

## K. Flujo de emisión (recomendado)

```text
POST /invoices (Idempotency-Key)
 SYNC  1 validar + idempotencia (fila idempotency_record + UNIQUE)         [TX1]
 SYNC  2 asignar número (fila fiscal_sequence FOR UPDATE)                 [TX1]
 SYNC  3 CUFD vigente (BD → lock si renovar)                              [TX1]
 SYNC  4 calcular montos + CUF (fecha fijada UNA vez y persistida)        [TX1]
 SYNC  5 XML → XSD → firma → gzip → sha256, PERSISTIR todo                [TX1 commit] ← punto de no retorno local
 SYNC  6 SiatPort.recepcionFactura (timeout acotado)  → fuera de TX
 SYNC  7 persistir respuesta + evento + outbox                            [TX2]
 ASYNC 8 QR + PDF + notificación al comprador
 ASYNC 9 si timeout/UNKNOWN → reconciliación (verificacionEstadoFactura)
```
Devolver al ERP: **200/201** si SIAT respondió 908 (validado); **422** si 904 (observado, con errores); **202** si estado desconocido (se reconcilia); en contingencia: **201 con `emissionType=OFFLINE`**.

## L. CUIS

- Qué es: código alfanumérico que identifica la relación sistema + credenciales + contribuyente + sucursal + (opcional) POS; **vigencia 365 días**; se obtiene con Token Delegado. [CONFIRMADO]
- Operación `cuis` en `FacturacionCodigos`; entrada: `codigoAmbiente`(1 prod, 2 piloto), `codigoSistema`, `nit`, `codigoModalidad`(1/2), `codigoSucursal`, `codigoPuntoVenta`; salida: `codigo`, `fechaVigencia`, `transaccion`, mensajes. Hay también `cuisMasivo`.
- **Renovable desde el 5.º día antes del vencimiento**; tras renovar hay que obtener nuevo CUFD. Al pedir CUFD el SIN avisa si el CUIS está por vencer. [CONFIRMADO]
- Diseño: `CuisService` (use case), `CuisClient` (puerto→SOAP), `CuisRepository`; job diario que renueva a T-5 días. Endpoints: `POST /fiscal/v1/cuis/requests`, `GET /fiscal/v1/cuis`.
- **[NO CONFIRMADO]**: tabla de códigos de error propios de CUIS (hay página "Códigos Error SIAT" sin leer) e invalidación explícita.

## M. CUFD

- Vigencia **24 h**, se solicita **diariamente** por sucursal/POS con el CUIS vigente; devuelve `codigo`, `codigoControl` (se usa en el CUF), `direccion`, `fechaVigencia`. Operaciones `cufd` y `cufdMasivo`. [CONFIRMADO]
- Recomendación: sincronizar fecha/hora (`sincronizarFechaHora`) antes de pedir CUFD (lo recomienda el SIN).
- **Anti-duplicados entre instancias**: `pg_advisory_xact_lock(hash(branch,pos))` + re-lectura dentro del lock + `UNIQUE` parcial "una ACTIVE". Quien obtiene el lock revalida y recién entonces llama al SIAT. Redis solo cachea el valor; el lock de verdad vive en PostgreSQL.
- Renovación anticipada: job programado (p. ej. 30–60 min antes del vencimiento), conservando el anterior para contingencia/histórico. El CUFD que debe usarse en offline es el vigente al entrar en contingencia. [CONFIRMADO]
- **[PENDIENTE]**: si el SIN permite pedir el CUFD siguiente antes de vencer el actual y devuelve el mismo; probar en piloto.

## N. CUF — algoritmo oficial (verificado)

Fuente: [Generación CUF](https://siatinfo.impuestos.gob.bo/index.php/facturacion-en-linea/algoritmos-utilizados/generacion-cuf), [Módulo 11](https://siatinfo.impuestos.gob.bo/index.php/facturacion-en-linea/algoritmos-utilizados/algoritmo-modulo-11), [Base 16](https://siatinfo.impuestos.gob.bo/index.php/facturacion-en-linea/algoritmos-utilizados/base-16).

Cadena de 53 dígitos, con relleno de ceros a la izquierda, en este orden:

| # | Campo | Largo |
|---|---|---|
| 1 | NIT emisor | 13 |
| 2 | Fecha/hora emisión `yyyyMMddHHmmssSSS` | 17 |
| 3 | Sucursal | 4 |
| 4 | Modalidad (1,2,3) | 1 |
| 5 | Tipo emisión (1 online, 2 offline, 3 masiva) | 1 |
| 6 | Tipo factura/doc. ajuste (1,2,3) | 1 |
| 7 | Documento sector | 2 |
| 8 | Número de factura | 10 |
| 9 | Punto de venta | 4 |

Luego: `d = mod11(cadena53)` (pesos 2..9 de derecha a izquierda; `resto 10→"1"`, `11→"0"`) → cadena de 54 → **convertir a hexadecimal (BigInteger, mayúsculas)** → **concatenar `codigoControl` del CUFD**. `CUF = hex(cadena54) + codigoControl`.

**Verificado:** reproduje con script el ejemplo oficial: `8727F63A15F8976591FDDE5B387C5D015A29E06A1` + `A19E23EF34124CD`. ✔

Pseudocódigo:
```text
pad(v,n) = v.zfill(n)  // falla si len(v)>n
c = pad(nit,13)+fecha17+pad(suc,4)+mod+emi+tipoFac+pad(sector,2)+pad(num,10)+pad(pos,4)
c54 = c + mod11(c, lim=9)
cuf = hex_upper(BigInteger(c54)) + cufd.codigoControl
```
Tests: ejemplo oficial; NIT de 13 dígitos; POS=0; número 10 dígitos; `dig=10` y `dig=11`; fecha con ms=000; entradas desbordadas (debe fallar); idempotencia (mismo input ⇒ mismo CUF).
Unicidad: la fecha/hora con milisegundos + número único por (sucursal,POS,sector) ⇒ CUF único; reforzado por `UNIQUE(company_id,cuf)`. **El CUF se calcula una sola vez y se persiste**; un reintento reutiliza el mismo CUF y XML (el CUF incluye la hora; regenerarlo crearía otra factura).
Aclaración [NO CONFIRMADO]: la página de Módulo 11 dice que la entrada no incluye POS, pero el ejemplo y la tabla sí; seguí el ejemplo oficial (53 dígitos incluyendo POS), que reproduce el resultado.

## O. XML / XSD

- Raíz por sector, ej. `facturaElectronicaCompraVenta` (y `facturaComputarizadaCompraVenta`), **sin namespace** (`noNamespaceSchemaLocation`), importa `http://www.w3.org/2000/09/xmldsig#` (SignatureSchema). Hay un XSD **distinto** para electrónica y para computarizada. 44 `element name` en el XSD de Compra-Venta. Versión del XSD descargado: **"ver. 23/08/2021"**. [CONFIRMADO] ([Compra-Venta](https://siatinfo.impuestos.gob.bo/index.php/facturacion-en-linea/archivos-xml-xsd-de-facturas-electronicas/factura-de-compra-y-venta), [ZIP](https://siatinfo.impuestos.gob.bo/images/archivos_tecnicos/archivos_apoyo/CompraVentaXML.zip))
- **Cabecera**: nitEmisor, razonSocialEmisor, municipio, telefono(opc), numeroFactura, cuf, cufd, codigoSucursal, direccion, codigoPuntoVenta(opc, `xsi:nil`), fechaEmision(UTC extendido sin zona, ej. `2020-02-15T08:40:12.215`), nombreRazonSocial(opc), codigoTipoDocumentoIdentidad(1–5), numeroDocumento, complemento(opc), codigoCliente, codigoMetodoPago, numeroTarjeta(opc, enmascarado), montoTotal, montoTotalSujetoIva, codigoMoneda, tipoCambio, montoTotalMoneda, montoGiftCard(opc), descuentoAdicional(opc), codigoExcepcion(opc), cafc(opc), leyenda, usuario, codigoDocumentoSector.
- **Detalle**: actividadEconomica, codigoProductoSin, codigoProducto, descripcion, cantidad, unidadMedida, precioUnitario, montoDescuento(opc), subTotal, numeroSerie(opc), numeroImei(opc).
- Regla: servicios ⇒ cantidad 1, unidad 58. Longitudes/enumeraciones: se extraen del XSD en pruebas de contrato (no las transcribo a mano).
- Diseño: `XmlGenerator` (por `XmlSchemaVersion` + sector), `XmlValidator` (XSD cargados desde recursos versionados con hash), `XmlSerializer` (determinista, sin reformateo posterior a la firma). Cambios del SIN ⇒ nueva versión de mapper + XSD, dominio intacto.
- Tres ejes de versión: `XmlSchemaVersion` · `FiscalRuleVersion` · `SiatApiVersion` (+ `TaxCalculationVersion`). Todos guardados en cada `fiscal_document`.

## P. Firma digital

- Aplica **solo a modalidad Electrónica en Línea** (la Computarizada no firma, usa el SHA-256 del gzip como huella). [CONFIRMADO]
- Estándar **XMLDSig**; pasos oficiales: canonicalización → SHA-256 → Base64 (DigestValue) → firmar SignedInfo con **RSA-SHA256** → Base64 (SignatureValue) → X509Certificate. La firma debe ser compatible con `SignatureSchema.xsd`. ([Firma digital](https://siatinfo.impuestos.gob.bo/index.php/facturacion-en-linea/firma-digital/firma-digital), [SignatureSchema](https://siatinfo.impuestos.gob.bo/index.php/facturacion-en-linea/firma-digital/signatureschema)) [CONFIRMADO]
- Causas típicas de "firma inválida": SHA-256 mal usado, certificado distinto al entregado para pruebas, `X509Certificate` mal ubicado, validar contra XSD computarizado, **modificar el XML después de firmar**. ([Firma inválida](https://siatinfo.impuestos.gob.bo/index.php/facturacion-en-linea/firma-digital/firma-invalida))
- Certificados válidos: AGETIC y Digicert (Bolivia). Validación de vigencia inmediata; **revocación no inmediata** (según CRL de la entidad); documentos firmados con certificado revocado se observan después. Existe el servicio `notificaCertificadoRevocado`. [CONFIRMADO]
- **[NO CONFIRMADO]**: URIs exactas de algoritmos de canonicalización/transform, y tipo de certificado exigido a un proveedor (¿un certificado por contribuyente o uno del proveedor?). **Esto es crítico para SaaS → decisión D-03.**
- Diseño: `DigitalSignatureService`, `CertificateService`, `CertificateValidator` (vigencia, cadena, CRL/OCSP). **La private key no se almacena en BD ni en repo.**
- Almacén de claves — recomendación: **AWS KMS** (clave de cifrado por tenant) + **Secrets Manager** para el PKCS#12 cifrado; firma con la clave en memoria solo durante la operación. Como el certificado lo emite una entidad boliviana y el contribuyente entrega un .p12, no se puede generar la clave dentro de KMS: hay que cargar la clave privada en memoria al firmar (alternativa: firmar con KMS solo si el SIN/entidad certificadora permite generar la clave en HSM [NO CONFIRMADO]). HSM/CloudHSM solo si el SIN o los clientes lo exigen (costo alto). Vault es equivalente si se prefiere no depender de AWS.

## Q. QR

- URL: `https://pilotosiat.impuestos.gob.bo/consulta/QR?nit={nit}&cuf={cuf}&numero={numero}&t={1|2}` (t: 1=rollo, 2=media hoja, por defecto 1). Mínimo recomendado 3×3 cm. **La URL de producción se informa al finalizar la autorización.** ([QR](https://siatinfo.impuestos.gob.bo/index.php/facturacion-en-linea/algoritmos-utilizados/codigo-respuesta-rapida-qr)) [CONFIRMADO]
- Por tanto `QrService` toma el dominio base de la configuración por ambiente, nunca hardcodeado. El "código de control" no forma parte del QR en este esquema.

## R. SOAP — servicios reales (WSDL v2)

Leí los WSDL de piloto (mismas rutas existen en producción con `siatrest`); namespaces `https://siat.impuestos.gob.bo/<Servicio>`. [CONFIRMADO por descarga]

| Servicio | URL piloto | Operaciones reales |
|---|---|---|
| FacturacionCodigos | `https://pilotosiatservicios.impuestos.gob.bo/v2/FacturacionCodigos?wsdl` | `cuis`, `cuisMasivo`, `cufd`, `cufdMasivo`, `verificarNit`, `verificarComunicacion`, `notificaCertificadoRevocado` |
| FacturacionSincronizacion | `…/v2/FacturacionSincronizacion` | `sincronizarActividades`, `sincronizarFechaHora`, `sincronizarListaActividadesDocumentoSector`, `sincronizarListaLeyendasFactura`, `sincronizarListaMensajesServicios`, `sincronizarListaProductosServicios`, `sincronizarParametricaEventosSignificativos`, `…MotivoAnulacion`, `…PaisOrigen`, `…TipoDocumentoIdentidad`, `…TipoDocumentoSector`, `…TipoEmision`, `…TipoHabitacion`, `…TipoMetodoPago`, `…TipoMoneda`, `…TipoPuntoVenta`, `…TiposFactura`, `…UnidadMedida`, `verificarComunicacion` |
| FacturacionOperaciones | `…/v2/FacturacionOperaciones` | `registroEventoSignificativo`, `consultaEventoSignificativo`, `registroPuntoVenta`, `registroPuntoVentaComisionista`, `consultaPuntoVenta`, `cierrePuntoVenta`, `cierreOperacionesSistema`, `verificarComunicacion` |
| ServicioFacturacionCompraVenta | `…/v2/ServicioFacturacionCompraVenta` | `recepcionFactura`, `anulacionFactura`, `reversionAnulacionFactura`, `verificacionEstadoFactura`, `recepcionPaqueteFactura`, `validacionRecepcionPaqueteFactura`, `recepcionMasivaFactura`, `validacionRecepcionMasivaFactura`, `recepcionAnexos`, `verificarComunicacion` |
| ServicioFacturacionElectronica | idem (sectores en general) | mismas + `recepcionAnexosSuministroEnergia` |
| ServicioFacturacionComputarizada | idem | idem |

Notas importantes:
- **Hay un servicio de recepción por sector/modalidad** ("publicados de forma diferenciada por tipo de documento sector"). El `SiatPort` debe elegir el cliente por (sector, modalidad).
- **Autenticación**: la página del token dice header `apikey: TokenApi <token>` (ejemplo Java); el sitio anexo/otra fuente indica `Authorization: Token <token>`. **Conflicto → probar en piloto [PENDIENTE]**; el adaptador debe tener el nombre de header configurable por versión.
- Recepción: `archivo` (XML gzip), `hashArchivo` (SHA-256 del gzip), `fechaEnvio`, `cufd`, `cuis`, `codigoDocumentoSector`, `codigoEmision`, `tipoFacturaDocumento`… Respuesta: `codigoEstado` **908 validada / 904 observada / 901 pendiente**, `codigoRecepcion`, `transaccion`, mensajes. [CONFIRMADO]
- Timeouts/retries/códigos de error: **[NO CONFIRMADO]** (no leí la tabla "Códigos Error SIAT"). Se definirán empíricamente en piloto.
- Recomendación: `verificarComunicacion` por recurso para entrar/salir de contingencia (práctica recomendada por el SIN).
- URL de producción: `siatrest.impuestos.gob.bo/v2/...` respondió 200 pero la URL oficial de producción **se entrega con la autorización** [PENDIENTE confirmar].

## S. Contingencia

Eventos significativos 1–7 ([página](https://siatinfo.impuestos.gob.bo/index.php/facturacion-en-linea/emision-y-envio-de-facturas/contingencia-y-eventos-significativos)) [CONFIRMADO]: 1 corte internet, 2 inaccesibilidad al servicio web SIN, 3 zona sin internet por despliegue de POS, 4 venta en lugares sin internet → emisión **fuera de línea con XML**; 5 virus/falla software, 6 cambio de infraestructura/falla hardware, 7 corte de energía → **facturas manuales de contingencia (CAFC)** o Portal Web transitorio.

Reglas: facturas offline con el **CUFD vigente antes del corte**, `codigoEmision=2`; paquetes **≤ 500**; gzip + SHA-256; **primero** pedir nuevo CUFD, **luego** `registroEventoSignificativo` (inicio/fin al minuto, código, CUFD del evento, CUFD de envío, descripción), **luego** `recepcionPaqueteFactura` (→ 901) y `validacionRecepcionPaqueteFactura` (→ 901/904/908). Evento registrado hasta **48 h** después de terminar; envío de paquetes offline en **48 h**, manuales transcritos en **72 h**. Masiva: ≤ 1000 (registro previo en portal). Buena práctica: guardar facturas sin código de respuesta y verificar con `verificacionEstadoFactura` para evitar duplicados. Con NIT offline: `codigoExcepcion=1`. "CONTINGENCIA" como tipo de emisión es de uso exclusivo del SIN.

Diseño: `ContingencyService` (máquina de entrada/salida automática por `verificarComunicacion`), `PackageService` (armado ≤500, gzip, hash, envío, validación, reintento), `ReconciliationService`. Discrepancia a resolver: el SIN habla de paquetes de **500** (contingencia) y **1000** (masiva), pero las pruebas de masiva mencionan **2000**. Usar 500/1000 como límite duro de producción y consultar el 2000 [PENDIENTE].

## T. Anulación

Hasta el **día 9 del mes siguiente** a la emisión, individual, solo si el documento está **válido en la BD del SIN** y **no fue usado en una DDJJ**; puede hacerse desde otra sucursal habilitada; se debe **notificar al comprador** (CUF/número/motivo). Exportación: 180 días (Fase II). Existe **`reversionAnulacionFactura`**. ([Anulación](https://siatinfo.impuestos.gob.bo/index.php/facturacion-en-linea/emision-y-envio-de-facturas/anulacion-de-documentos-fiscales)) [CONFIRMADO]. Motivos desde el catálogo `MotivoAnulacion`.
`POST /invoices/{id}/cancel` → validar estado ACCEPTED, ventana, permiso `FISCAL_ADMIN`/rol, motivo de catálogo → `CANCEL_PENDING` → `anulacionFactura` → `CANCELLED` solo con respuesta SIAT OK; nunca cancelar local. Cambio reciente: RND 25/2026 sobre anular tras DDJJ [NO CONFIRMADO, leído en fuente secundaria].

## U. Nota Crédito-Débito

Documento sector **24**, tipo documento = 3 (ajuste); se emite bajo la modalidad vigente del contribuyente (RND 11 Art. 36 a); para facturas de modalidades anteriores el sistema debe permitir **transcripción manual** de los datos de la original; hasta 18 meses. No leí el XSD de NCD (hay `Notas Crédito Débito` en siatanexo) → **[PENDIENTE]**: extraer campos y reglas de monto. Módulo desacoplado `adjustment`.

## V. Catálogos

18 sincronizaciones listadas arriba (actividades, leyendas, mensajes, productos/servicios, eventos, motivos anulación, país, doc. identidad, doc. sector, tipo emisión, habitación, método pago, moneda, punto de venta, tipo factura, unidad medida, fecha-hora, actividades×sector) — todas con `codigoAmbiente, codigoSistema, nit, cuis, codigoSucursal`. Sincronización **diaria**; con esquema centralizado puede hacerse una vez con Casa Matriz. Flujo: SIAT → `fiscal_catalog_version` (hash) → items → Redis. Cada documento guarda el set de versiones de catálogo usado.

## W. Seguridad (resumen)

OAuth2/OIDC + JWT con `tenant_id` y roles; RBAC (SUPER_ADMIN plataforma, TENANT_ADMIN, FISCAL_ADMIN [certificados, tokens, anulación], ACCOUNTANT, SELLER [emitir], AUDITOR [solo lectura], API_CLIENT [machine-to-machine con scopes]); RLS PostgreSQL; TLS 1.2+; mTLS opcional ERP↔Fiscal; secretos en Secrets Manager/KMS con rotación; token y certificado cifrados por tenant (envelope encryption); logs sin tokens/claves/tarjetas (la tarjeta se enmascara por norma); WAF + rate limit por tenant; anti-replay (Idempotency-Key + timestamp firmado en ERP); segregación: quien carga el certificado no emite.

## X. Auditoría

`audit_event` append-only con `prev_hash/hash` (cadena), `REVOKE UPDATE, DELETE`, trigger que rechaza UPDATE, backups WORM (S3 Object Lock). Campos: who, when, tenant, document, operation, oldState, newState, SIATRequest, SIATResponse (ref), IP, correlationId, traceId.

## Y. Observabilidad

Micrometer → Prometheus → Grafana; OTel (traces ERP→API→SOAP); métricas: `invoices.created|accepted|rejected|cancelled`, `siat.requests|errors|latency|timeouts`, `cufd.renewals`, `contingency.events`, `packages.sent|failed`. Alertas: CUFD no renovado < 2 h antes de vencer, CUIS < 5 días, SIAT error rate > X%, documentos en `UNKNOWN`/`SENDING` > N min, outbox lag, paquetes pendientes cerca de 48 h, certificado por vencer.

## Z. Resiliencia, idempotencia y reconciliación

Principio: **nunca reintentar a ciegas el envío de una factura**.

1. `Idempotency-Key` + hash del request: misma clave y mismo hash ⇒ devolver el resultado guardado; misma clave y distinto hash ⇒ 409.
2. El documento (número, CUF, XML firmado, hash) se persiste **antes** de llamar a SIAT (paso 5 del flujo). Un reintento reenvía **el mismo** archivo.
3. Si el envío termina en timeout/corte: estado `SENT_UNKNOWN`. El worker llama `verificacionEstadoFactura` (por CUF): si existe → se adopta el resultado; si no existe → se reenvía el mismo XML (el CUF idéntico evita duplicar una factura; **[NO CONFIRMADO]** cómo responde SIAT a un reenvío del mismo CUF → probar en piloto).
4. Circuit breaker por servicio SIAT + `verificarComunicacion` ⇒ si abre, el `ContingencyService` pasa a offline (con evento 2).
5. Bulkhead por tenant; backoff exponencial solo para operaciones **consultivas/idempotentes**; DLQ para mensajes Kafka con fallos repetidos y alarma.

## AA/AB. API REST y OpenAPI (borrador de contrato; OpenAPI completo se entrega tras aprobación)

Base `/api/v1`. Mejoras sobre tu lista: recursos anidados por contribuyente y `Idempotency-Key` obligatorio en POST; `202 Accepted` cuando el resultado es incierto.

```text
POST   /tenants                      GET /tenants/{id}
POST   /companies                    POST /companies/{id}/token       POST /companies/{id}/certificates
POST   /companies/{id}/branches      POST /branches/{id}/points-of-sale
GET    /companies/{id}/fiscal-configuration
POST   /branches/{id}/cuis:renew     GET /branches/{id}/cuis
POST   /branches/{id}/cufd:renew     GET /branches/{id}/cufd/current
POST   /invoices                     GET /invoices/{id}  /status  /xml  /pdf  /qr
POST   /invoices/{id}/cancel         POST /invoices/{id}/cancel:revert
POST   /adjustment-notes             (Nota C/D)
POST   /contingency/events           POST /packages        GET /packages/{id}
GET    /catalogs                     POST /catalogs/sync
GET    /health  (incl. SIAT connectivity)
```
Headers: `Idempotency-Key`, `X-Correlation-Id`, `traceparent`. Paginación por cursor. Error model:
```json
{"code":"FISCAL_001","category":"SIAT_ERROR","message":"…","details":[],"siatCode":"…","correlationId":"…","timestamp":"…"}
```
Categorías: BUSINESS, VALIDATION, FISCAL, SIAT, NETWORK, INFRASTRUCTURE, SECURITY.

## Máquina de estados (corregida)

Tus estados mezclan **pasos de proceso** con **estados fiscales**. Propuesta: estados de documento (persistentes) + eventos de proceso (en `fiscal_document_event`).

```text
DRAFT → VALIDATED → READY_TO_SEND (número+CUF+XML firmado persistidos)
READY_TO_SEND → SENDING → ACCEPTED(908) | OBSERVED(904) | SENT_UNKNOWN
SENT_UNKNOWN → ACCEPTED | OBSERVED | READY_TO_SEND (reintento, mismo CUF)
READY_TO_SEND → OFFLINE_PENDING (emisión offline) → PACKAGED → PACKAGE_SENT(901) → ACCEPTED | OBSERVED
ACCEPTED → CANCEL_PENDING → CANCELLED | ACCEPTED (rechazo SIAT)
CANCELLED → ACCEPTED (reversión de anulación, si SIAT lo permite)
OBSERVED → (la corrección crea un documento nuevo; no se muta el anterior)
```
Eliminé `SYNCED/PENDING_SYNC` (reemplazados por `OFFLINE_PENDING/PACKAGED/PACKAGE_SENT`) y `REJECTED` (SIAT usa "observada" 904; **[PENDIENTE]** cuándo una 904 deja la factura sin efecto y cuándo es solo advertencia). Cada transición guarda actor, timestamp, motivo, respuesta SIAT, `idempotencyKey`.

## Numeración (recomendación)

`fiscal_sequence` con `SELECT … FOR UPDATE` dentro de la TX1 (misma que persiste el documento): garantiza consecutivo sin huecos si la TX completa, y se serializa por (company, branch, POS, sector). Una `SEQUENCE` de PostgreSQL no se revierte y deja huecos; optimistic locking provoca reintentos bajo carga. Si hay mucha contención en un POS (≫100 TPS por POS, improbable) se asigna por bloques. **[PENDIENTE]** confirmar si SIAT exige numeración por NIT global, por sucursal o por POS ("numeración propia", no definida en lo que leí). Si TX1 hace rollback, el número no se consume. Huecos tras anulación no son un problema (se anula en SIAT).

## Kafka + Outbox + Redis

PostgreSQL es la verdad. `outbox_event` se escribe en la misma TX que el cambio de estado; un relay (Debezium o poller con `FOR UPDATE SKIP LOCKED`) publica a Kafka, consumidores idempotentes (clave = `event_id`). Eventos: `InvoiceCreated`, `InvoiceSigned`, `InvoiceSubmitted`, `InvoiceAccepted`, `InvoiceRejected(Observed)`, `InvoiceCancelled`, `ContingencyStarted`, `PackageCreated/Submitted/Accepted`. Kafka lleva notificaciones y trabajo asíncrono, **no** XML firmados ni estado definitivo (solo referencias).
Redis: caché de CUFD vigente, catálogos, configuración, rate limiting, locks de optimización. **No depende solo de Redis**: números, CUFD autoritativo, idempotencia, estados, tokens.

## AF. AWS y PDF

ECS Fargate + ALB + RDS PostgreSQL Multi-AZ + ElastiCache + MSK, Secrets Manager/KMS, WAF, CloudWatch/Managed Prometheus-Grafana. ECS sobre EKS porque es un solo servicio y equipo pequeño; EKS recién si se separan >5 servicios. IP de salida fija (NAT con EIP) por si el SIN exige lista blanca [NO CONFIRMADO].
PDF: **OpenPDF (LGPL/MPL) o Apache FOP/OpenHTMLtoPDF**; recomiendo OpenHTMLtoPDF + plantillas por sector (HTML/CSS) con QR vía ZXing. XML es el documento; PDF derivado. Las plantillas deben seguir el "Formato Gráfico" PDF oficial de cada sector.

## AD/AE. Testing y performance

Unit: CUF/mod11/base16, redondeo HALF-UP, impuestos, XML, firma. Contrato: XSD oficial + WSDL (generados desde las URL de arriba, con congelado en CI) contra mocks WireMock. Integración: Testcontainers (PG, Redis, Kafka). E2E: ERP → Fiscal → **SIAT piloto**. Carga: el SIN **no mide carga**; es obligación del proveedor (Fase 3). Objetivo de partida: 50 TPS sostenidos agregados, 100 TPS ráfaga con 2+ instancias; 500 TPS no es realista porque el cuello es la latencia del SIAT y el SOAP síncrono [INFERENCIA]; se mide en piloto antes de comprometer SLA.

## AG. CI/CD

Git → build/unit → integración (Testcontainers) → contrato XSD/WSDL → SAST/dependency scan → imagen Docker → deploy DEV → pruebas en SIAT piloto (secretos del contribuyente de prueba) → QA → aprobación manual → PROD. Versionado semántico del motor fiscal ligado a `sistema.version` registrado en el SIN (cambiar versión puede requerir re-notificación [PENDIENTE]).

## AH. Checklist de autorización (proveedor)

- [ ] Persona jurídica con NIT activo, IVA, sin marcas
- [ ] Credenciales SIAT en Línea
- [ ] Alta de sistema en **piloto** (tipo = proveedor, modalidad, sectores, contactos) → obtener `codigoSistema`
- [ ] Certificado de prueba (CSR AGETIC) si modalidad Electrónica
- [ ] Un contribuyente de prueba con token delegado piloto
- [ ] Fase 1: CUIS, catálogos, CUFD, 500 emisiones/sector, eventos, anulaciones (250), firma (250), masivo (si aplica)
- [ ] Fase 2: inspección (15 puntos listados en §D)
- [ ] Fase 3: asociación cliente↔proveedor, piloto funcional + carga propia
- [ ] Sistema **WEB y API** autorizados por separado
- [ ] URL de producción y QR de producción recibidos
- [ ] Declaración jurada de inicio de operaciones
- [ ] Plan de actualización cuando cambie el Anexo Técnico (Ley 1733)

## AI. Roadmap

| Fase | Objetivo | Entregable clave | Riesgo principal | Criterio de aceptación |
|---|---|---|---|---|
| 0 Investigación | Cerrar PENDIENTES | Informe validado con el SIN | Documentación incompleta | Lista de decisiones D-xx resuelta |
| 1 Arquitectura | Aprobar este doc | ADRs, OpenAPI v0, DDL | Sobrediseño | Aprobación |
| 2 Foundation | Multi-tenant, seguridad, outbox, audit | Esqueleto Spring + Flyway + RLS | Mala base de tenancy | Test de aislamiento verde |
| 3 SIAT connectivity | Clientes SOAP, token, CUIS, CUFD, sync | Casos Fase 1 I–III | Header de auth ambiguo | Etapas I–III vencidas |
| 4 Fiscal Engine | CUF, XML, XSD, firma, numeración | Libs probadas con ejemplo oficial | Firma inválida | Firma aceptada por piloto |
| 5 Invoice | Sector 1 end-to-end, QR/PDF, anulación | 500+250+250 casos | Idempotencia | Fase 1 IV–VIII |
| 6 Contingencia | Eventos, offline, paquetes, reconciliación | Fase 1 V + Fase 2 | Plazos 48/72 h | Pruebas de corte |
| 7 Nota C/D | Sector 24 | módulo `adjustment` | XSD no leído | Casos NCD |
| 8 Integración ERP | Cliente REST/SDK para SOLID ERP | Contrato estable | Acoplamiento | E2E |
| 9 Testing | Carga y caos | Informe de capacidad | Latencia SIAT | SLA definido |
| 10 Autorización | Fases 2 y 3 | Documentación | Ventana 01/oct–16/nov o siguiente | Autorizado |
| 11 Producción | Go-live | Runbooks, on-call | Datos de prod | Operación estable |

## AJ. Sprints iniciales (7 días)

| Sprint | Objetivo | Entregable |
|---|---|---|
| S0 | Resolver D-01…D-05, crear repo y ADRs | ADRs aprobados |
| S1 | Proyecto Maven hexagonal, CI, Flyway base, tenant/company/branch/POS, RLS + tests de aislamiento | Esqueleto + tests |
| S2 | Seguridad (OIDC, RBAC), auditoría append-only, idempotency_record, outbox | Cadena de hashes funcionando |
| S3 | Cliente SOAP generado desde WSDL, `SiatPort`, token cifrado, `verificarComunicacion`, sincronizar fecha/hora | Conexión a piloto |
| S4 | CUIS + CUFD (con advisory lock) + jobs de renovación | Etapas I y III |
| S5 | Catálogos (18 sync) + Redis | Etapa II |
| S6 | CUF + numeración + `TaxCalculator` v1 + modelo monetario | Tests con ejemplo oficial |
| S7 | XML Compra-Venta + XSD + gzip + sha256 (modalidad computarizada o electrónica) | Contrato XSD verde |
| S8 | Firma XMLDSig + certificados | Firma aceptada |
| S9 | `POST /invoices` completo + estados + reconciliación | 1.ª factura 908 en piloto |

(El detalle de historias/tareas/DoD por sprint se redacta después de la aprobación; hacerlo ahora sería especulativo.)

## AK. Riesgos

1. **Anexo Ley 1733 en construcción**: puede obligar a rehacer XML/servicios antes de producción. Mitigación: versionado + dominio neutro.
2. **Ventana de autorización 01/oct–16/nov**: casi imposible cumplir con un sistema nuevo; probablemente se apunte a una prórroga/siguiente convocatoria [PENDIENTE confirmar con el SIN].
3. Modelo de **certificado/token por contribuyente**: onboarding fricción alta.
4. Comportamiento de SIAT ante reenvío del mismo CUF / latencias: desconocido hasta piloto.
5. Header de autenticación ambiguo.
6. Incompleta investigación de códigos de error y de NCD/moneda extranjera.
7. Retención y conservación legal no confirmada.
8. Cambios de naming de sectores (ej. sector 55) → no usar nombres como claves.

## AL. Decisiones a tomar antes de programar

| ID | Decisión | Mi recomendación |
|---|---|---|
| D-01 | ¿Autorizar por la RND 11 vigente o esperar el Anexo Ley 1733? | Construir contra RND 11/v2 y mantener capa de versionado; preguntar al SIN si hay nueva convocatoria |
| D-02 | Modalidad inicial: **Electrónica (firma)** vs **Computarizada (sin firma)** | Computarizada primero para abreviar (sin PKI), Electrónica en el siguiente hito; **tú dijiste firma en el MVP → confirma** |
| D-03 | ¿Certificado digital por contribuyente o del proveedor? | Confirmar con el SIN en la fase de piloto; asumir por contribuyente |
| D-04 | ¿Usarás tú mismo como primer contribuyente (SOLID ERP) o tendrás clientes desde el día 1? | Primero un NIT propio en piloto |
| D-05 | Sectores del MVP | Sector 1 + 24 |
| D-06 | Alcance Web + API | Autorizar solo la API primero |
| D-07 | Nube | AWS (ECS) |
| D-08 | Persona jurídica/NIT que será el "proveedor" ante el SIN | Debe existir ya |

## Qué NO investigué todavía (declarado)

Códigos de error SIAT, XSD de Nota C/D, reglas de moneda extranjera vigentes a sept-2026 más allá del sector 9, `registroEventoSignificativo` completo, `cuisMasivo/cufdMasivo`, `verificarNit`, esquemas de despliegue, homologación de productos, texto completo de RND 11/Ley 1733, retención legal, SLA del SIAT. Cada uno se cierra en la Fase 0/sprint S0 o con consulta al SIN.
