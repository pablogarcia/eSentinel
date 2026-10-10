package bo.solid.fiscal.domain.siat;

/** Alcance sin CUIS (para solicitarlo): sucursal y punto de venta (0 = nivel sucursal). */
public record BranchScope(int branchCode, int posCode) {
    public BranchScope {
        if (branchCode < 0 || posCode < 0) throw new IllegalArgumentException("Codigos de sucursal/POS invalidos");
    }
}
