# ADR-0011 — Adaptador SOAP del SIAT, token delegado y bitácora (S3)

Estado: Aceptado · 2026-10-10

## Cliente SOAP
- Clientes **generados en compilación** (CXF `wsdl2java`) desde los WSDL v2 del piloto, versionados en `src/main/resources/siat/wsdl/v2/` con `MANIFEST.sha256`. Hoy se generan Codigos, Sincronizacion y Operaciones; los servicios de facturación (CompraVenta/Electronica/Computarizada) se generan cuando se usen (S7+). Cada servicio va a su propio paquete (`infrastructure.siat.generated.*`) porque todos comparten el namespace `https://siat.impuestos.gob.bo/`.
- ArchUnit: solo `infrastructure.siat` puede tocar las clases generadas. El dominio usa `SiatPort` y tipos propios (`SiatCredentials`, `CommunicationStatus`, `SiatDateTime`).
- Se usa `JaxWsProxyFactoryBean` con la interfaz generada (sin leer el WSDL en runtime): no depende de rutas de archivo y permite compartir proxies entre contribuyentes. El token viaja por un `ThreadLocal` acotado a la llamada sincrónica y se inyecta como header HTTP.
- El contrato se vigila con `SiatContractTest`: offline (hashes) y opt-in `-Dsiat.live=true` (WSDL del piloto idénticos a los versionados + llamada real con token inválido).

## Autenticación (cierra P-03, verificado contra el piloto el 2026-10-10)
- El header reconocido es **`apikey`** (valor `TokenApi <token>` según la doc oficial). `Authorization: Token …` se ignora ("EL SERVICIO REQUIERE API KEY").
- **Incluso `verificarComunicacion` exige token.** Un token inválido devuelve un **SOAP Fault con HTTP 500** ("API KEY NO VALIDO"), no un 401. Por eso la clasificación mira el texto del fault (`API KEY` → `SIAT_AUTH_001`).
- No verificado: si el prefijo `TokenApi ` es obligatorio o basta el token solo (ambos dieron "NO VALIDO" con token falso). Es configurable; confirmar con un token real.

## Clasificación de fallas (sin reintentos en el adaptador)
| Causa | Código | HTTP API | Resultado de una escritura |
|---|---|---|---|
| Fault "API KEY…" / HTTP 401-403 | `SIAT_AUTH_001` | 502 | no se procesó |
| Conexión rechazada / DNS / TLS / HTTP 5xx / timeout de conexión | `SIAT_NET_001` | 503 | no se procesó (seguro reintentar) |
| Timeout de lectura | `SIAT_NET_002` | 504 | **DESCONOCIDO** → verificar antes de reenviar (ADR-0005) |
| Otro fault / HTTP 4xx / respuesta ilegible | `SIAT_PROTO_001` | 502 | revisar |

## Token delegado
- Write-only por API (`PUT /companies/{id}/siat-token`); se guarda con **AES-256-GCM**, IV aleatorio, **AAD = tenant+empresa** (un ciphertext copiado a otra empresa no descifra), `key_id` para rotación. Un nuevo token revoca el anterior (único ACTIVE por empresa). La auditoría registra solo metadatos.
- El cifrador local es para dev/test y **falla al arrancar sin clave** (no hay modo "sin cifrar"). Producción debe sustituirlo por un adaptador **AWS KMS** (envelope encryption) detrás del mismo puerto `SecretCipher` — **pendiente (S10 / infra)**.
- Riesgo residual: con el cifrador local la clave maestra vive en la misma app que la BD; no protege contra un atacante con ambos accesos.

## Bitácora `siat_transaction`
- Cada llamada: `STARTED` confirmado en TX propia **antes** de llamar, llamada **sin transacción abierta**, `finish` en otra TX con `outcome`/latencia. Un `STARTED` huérfano (caída del proceso) es candidato de reconciliación (S9).
- Métricas Micrometer: `siat.requests`, `siat.latency`, `siat.errors`, `siat.timeouts` (tags `operation`, `outcome`).

## Pendiente de S3
- `sincronizarFechaHora` está implementado y probado contra el simulado, pero **no contra el SIAT real** (necesita CUIS válido → S4).
- Sin circuit breaker / bulkhead todavía (S9). El timeout (conexión 5 s, lectura 15 s) es configurable y provisional hasta medir P-07.
- Endpoint de producción `siatrest…/v2` responde pero su URL oficial se entrega al autorizar el sistema.
