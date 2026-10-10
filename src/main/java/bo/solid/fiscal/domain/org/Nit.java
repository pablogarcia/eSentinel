package bo.solid.fiscal.domain.org;

/** NIT del contribuyente. Rango segun el XSD oficial (1..9999999999999, 13 digitos). */
public record Nit(long value) {

    public static final long MAX = 9_999_999_999_999L;

    public Nit {
        if (value < 1 || value > MAX) {
            throw new IllegalArgumentException("NIT fuera de rango: debe estar entre 1 y " + MAX);
        }
    }
}
