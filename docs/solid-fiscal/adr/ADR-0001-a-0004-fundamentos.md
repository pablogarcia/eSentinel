# ADRs fundamentales (S0)

Formato: Contexto · Decisión · Consecuencias · Fuente. Estado: **Aceptado** salvo indicación. Fecha: 2026-10-09.

---

## ADR-0001 — Monolito modular hexagonal (`fiscal-invoicing-service`)
- **Contexto:** equipo pequeño, un solo flujo crítico (emisión). Prioridad: integridad > mantenibilidad > performance.
- **Decisión:** un servicio Spring Boot 3 / Java 21 con paquetes `domain`, `application`, `infrastructure`, `interfaces`. Reglas ArchUnit: `domain` no importa Spring, JAX-WS ni JPA; `interfaces` no llama a `infrastructure`. Un mismo artefacto con perfiles `api` y `worker`.
- **Consecuencias:** separar un worker de SIAT en otro servicio queda posible porque el único contacto es `SiatPort` + Kafka.

## ADR-0002 — Multi-tenant: schema compartido + `tenant_id` + Row Level Security
- **Contexto:** SaaS futuro, aislamiento estricto exigido.
- **Decisión:** toda tabla lleva `tenant_id`; RLS con `SET LOCAL app.tenant_id` por transacción; claves únicas compuestas con `tenant_id`; el rol de la aplicación NO es dueño de las tablas (para que RLS no se evada). Test de aislamiento obligatorio en CI.
- **Alternativas descartadas:** schema por tenant (Flyway ×N), DB por tenant (costo). Se deja abierta para clientes grandes.
- **Consecuencias:** jobs de fondo deben fijar el tenant explícitamente; un rol `fiscal_admin_job` separado y auditado para tareas globales.

## ADR-0003 — Modalidad inicial: **Electrónica en Línea** (decidido por el usuario, 2026-10-09)
- **Contexto:** el SIN define Electrónica en Línea (código 1, con XMLDSig) y Computarizada en Línea (2, sin firma, huella = SHA-256 del gzip). Fuente: [Sistema Informático](https://siatinfo.impuestos.gob.bo/index.php/facturacion-en-linea/requerimientos/sistema-informatico).
- **Decisión:** el MVP emite en modalidad 1. La modalidad es un parámetro de `fiscal_configuration`, no una constante: existirá `SigningStrategy` (`XmlDsigStrategy` ahora; `NoSignatureStrategy` después) y un `XsdSet` por modalidad (el XSD electrónico y el computarizado son distintos).
- **Consecuencias (trabajo extra que esto trae):**
  1. Se necesita certificado digital desde el sprint S3: de prueba vía CSR (formato AGETIC) enviado al SIN; en producción AGETIC o Digicert. Es **ruta crítica externa**: arrancar el trámite ya.
  2. Etapa VIII de Fase 1 (250 emisiones firmadas por sector) es obligatoria.
  3. Validar contra el XSD electrónico; no modificar el XML tras firmar.
  4. En CUF, `modalidad = 1`; en `cuis`/`cufd`, `codigoModalidad = 1`.
  5. Servicio de recepción: `ServicioFacturacionElectronica` (genérico) y `ServicioFacturacionCompraVenta` para el sector 1 — **[PENDIENTE]** confirmar en piloto cuál corresponde a Compra-Venta electrónica (el WSDL `CompraVenta` no tiene operaciones de anexos de energía; el `Electronica` sí).
- **Riesgo abierto:** D-03 (certificado por contribuyente o del proveedor).

## ADR-0004 — Construir contra RND 102100000011 / SOAP v2; Ley 1733 como evolución versionada
- **Contexto:** la RND 34/2026 dice que hasta que entre en vigencia la RND de implementación rige la RND 11/2021; el Anexo Ley 1733 es v0.0 sin XSD.
- **Decisión:** cuatro ejes de versión persistidos en cada documento: `fiscal_rule_version`, `xml_schema_version`, `siat_api_version`, `tax_calculation_version` (+ conjunto de versiones de catálogo). Valores iniciales: `RND11-2021`, `XSD-2021-08-23` (según comentario del XSD), `SOAP-V2`, `TAX-CLASSIC-HALFUP2`. IVA Transparente = nuevos valores + nueva implementación de `TaxCalculator`/`XmlMapper`, nunca modificación de las existentes.
- **Consecuencias:** una factura histórica se reconstruye con la versión que tenía. Los XSD se guardan en el repo bajo `src/main/resources/siat/xsd/<versión>/` con su SHA-256; un test de contrato falla si cambia el hash. Vigilancia: revisar semanalmente `versionamiento-2026` y `siatanexo` (tarea de proceso).
