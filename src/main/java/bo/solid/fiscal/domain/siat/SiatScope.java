package bo.solid.fiscal.domain.siat;

/** Sucursal / punto de venta / CUIS sobre los que se hace una consulta (0 en POS = no corresponde). */
public record SiatScope(int branchCode, int posCode, String cuis) {
    public SiatScope {
        if (branchCode < 0 || posCode < 0) throw new IllegalArgumentException("Codigos de sucursal/POS invalidos");
        if (cuis == null || cuis.isBlank()) throw new IllegalArgumentException("CUIS requerido");
    }

    @Override
    public String toString() {
        return "SiatScope[branchCode=" + branchCode + ", posCode=" + posCode + "]";
    }
}
