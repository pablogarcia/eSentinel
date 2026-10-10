# Sprint S0 — Registro de decisiones y acciones externas

Fecha: 2026-10-09 · Objetivo S0: cerrar decisiones, ADRs y trámites externos que bloquean los sprints siguientes. **Sin código.**

## 1. Decisiones

| ID | Decisión | Estado | Resolución |
|---|---|---|---|
| D-01 | Construir contra RND 11/2021 + SOAP v2; Ley 1733 como evolución versionada | **Cerrada** (aprobación global del usuario, recomendación aceptada) | ADR-0004 |
| D-02 | Modalidad inicial | **Cerrada por el usuario: Electrónica en Línea** | ADR-0003 |
| D-03 | Certificado por contribuyente vs. del proveedor | **Abierta** — se asume "por contribuyente" y se confirma con el SIN | Acción A-05 |
| D-04 | Primer contribuyente en piloto | **Abierta** — asumido NIT propio de SOLID | Necesito el NIT y el nombre de la entidad |
| D-05 | Sectores del MVP | **Asumida**: 1 (Compra-Venta) y 24 (Nota Crédito-Débito) | Confirmar |
| D-06 | Autorizar API primero (Web aparte) | **Asumida**: solo API | Confirmar |
| D-07 | Nube | **Asumida**: AWS ECS Fargate | ADR de despliegue en S1 |
| D-08 | Entidad/NIT que figura como proveedor ante el SIN | **Abierta** | Requisitos: NIT activo, obligación IVA, sin marcas ([Proceso](https://siatinfo.impuestos.gob.bo/index.php/facturacion-en-linea/autorizacion-de-sistemas/proceso-de-autorizacion)) |

D-04…D-08 están marcadas "asumida" porque respondiste "Aprobado" en bloque; si alguna no es correcta, dímelo antes de S1.

## 2. Acciones externas (las debes ejecutar tú; no puedo hacerlas por ti)

Estas acciones requieren tus credenciales y datos personales/corporativos, así que no las ejecuto yo.

| # | Acción | Dónde | Bloquea |
|---|---|---|---|
| A-01 | Ingresar al portal con SIAT en Línea y abrir **Gestión de Autorización de Sistemas (PILOTO)** → *Autorización de Sistemas Informáticos de Facturación* → *Nuevo*. Datos: nombre comercial "SOLID Fiscal Bolivia", **tipo: Proveedor**, versión, modalidad **Electrónica en Línea**, sectores 1 y 24, contactos | [siat.impuestos.gob.bo](https://siat.impuestos.gob.bo/) | S3 (se obtiene el `codigoSistema`) |
| A-02 | Generar **CSR** (formato AGETIC) y pedir al SIN el **certificado de prueba**. Guías oficiales: [CSR para software](https://siatinfo.impuestos.gob.bo/index.php/facturacion-en-linea/firma-digital/generacion-csr/generacion-de-csr-para-software) | Correo al SIN según la guía | S8 (firma) — **ruta crítica, mayor plazo** |
| A-03 | Generar **Token Delegado Piloto** del NIT de prueba: Gestión de Autorización → *Token Delegado Piloto* → *Generar Nuevo Ticket* | Portal | S3 |
| A-04 | Pedir el **Excel de casos de prueba** de cada etapa (CUIS, Catálogos, CUFD, Emisión, Eventos, Anulación, Firma) | Páginas de Fase 1 | S4–S9 |
| A-05 | Consultar al SIN: (a) certificado por contribuyente o del proveedor, (b) numeración (NIT/sucursal/POS), (c) nueva convocatoria si no se llega al 16/nov/2026, (d) URL de producción, (e) retención legal, (f) plazos y entrega de los 2000 en masivo | Canal oficial del SIN | Fase 0 |
| A-06 | Decidir cuenta AWS y región | — | S1 |

Notas de seguridad: el token delegado y el .p12 no deben pegarse en chat ni en Git. Cuando los tengas, los cargamos por Secrets Manager / `.env.local` ignorado por git.

## 3. Pruebas a ejecutar contra el piloto en cuanto exista token (S3)

| ID | Prueba | Cierra |
|---|---|---|
| P-01 | Reenviar `recepcionFactura` con el mismo CUF/archivo ¿rechaza, devuelve la misma recepción o duplica? | ADR-0005 |
| P-02 | Reutilizar un `numeroFactura` en otro POS/sucursal | ADR-0006 |
| P-03 | Header de autenticación: `apikey: TokenApi <t>` vs `Authorization: Token <t>` | Adaptador |
| P-04 | ¿Se puede pedir CUFD antes de vencer el actual? ¿devuelve el mismo? | ADR-0007 |
| P-05 | ¿Qué servicio recibe Compra-Venta electrónica: `ServicioFacturacionCompraVenta` o `…Electronica`? | ADR-0003 |
| P-06 | Qué casos de 904 invalidan la factura y cuáles son advertencias | Máquina de estados |
| P-07 | Latencia p50/p95 de `recepcionFactura` | Capacidad |

## 4. Entregables S0

- [x] Informe de investigación (`01-investigacion-y-arquitectura.md`)
- [x] ADR-0001 a ADR-0009 (`adr/`)
- [x] Registro de decisiones y acciones externas (este archivo)
- [ ] Repositorio inicializado — **pendiente: el directorio actual (`eSentinel/api`) no es un repo git y contiene otro proyecto; necesito que me indiques dónde crear `solid-fiscal`**
- [ ] Confirmación de D-04…D-08

## 5. Definition of Done de S0

Decisiones D-01…D-08 cerradas o con dueño; A-01 a A-03 iniciadas; ubicación del repo decidida. Entonces arranca **S1**: Maven hexagonal, CI, Flyway base, tenant/company/branch/POS, RLS + test de aislamiento.
