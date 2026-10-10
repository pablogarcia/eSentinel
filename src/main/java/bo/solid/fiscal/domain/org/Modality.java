package bo.solid.fiscal.domain.org;

/** codigoModalidad del SIAT. Solo ELECTRONIC_ONLINE esta soportada en el MVP (ADR-0003). */
public enum Modality {
    ELECTRONIC_ONLINE(1, true), COMPUTERIZED_ONLINE(2, false), WEB_PORTAL_ONLINE(3, false);

    private final int code;
    private final boolean supported;

    Modality(int code, boolean supported) {
        this.code = code;
        this.supported = supported;
    }

    public int code() { return code; }

    public boolean supported() { return supported; }

    public static Modality fromCode(int code) {
        for (Modality m : values()) {
            if (m.code == code) return m;
        }
        throw new IllegalArgumentException("Modalidad desconocida: " + code);
    }
}
