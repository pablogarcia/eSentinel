package bo.solid.fiscal.domain.siat;

/** Mensaje de servicio devuelto por el SIAT (codigo + descripcion). */
public record SiatMessage(Integer code, String description) {}
