package bo.solid.fiscal.interfaces.rest;

import java.time.Instant;
import java.util.List;

/** Modelo de error unico de la API (seccion 40). siatCode se rellena en errores de categoria SIAT. */
public record ApiError(String code, String category, String message, List<String> details, String siatCode,
                       String correlationId, Instant timestamp) {
}
