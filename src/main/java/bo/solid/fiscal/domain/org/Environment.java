package bo.solid.fiscal.domain.org;

/** codigoAmbiente del SIAT: 1 produccion, 2 pruebas y piloto. */
public enum Environment {
    PRODUCTION(1), PILOT(2);

    private final int code;

    Environment(int code) { this.code = code; }

    public int code() { return code; }

    public static Environment fromCode(int code) {
        for (Environment e : values()) {
            if (e.code == code) return e;
        }
        throw new IllegalArgumentException("Ambiente desconocido: " + code);
    }
}
