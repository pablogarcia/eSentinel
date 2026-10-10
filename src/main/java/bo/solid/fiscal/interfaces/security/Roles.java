package bo.solid.fiscal.interfaces.security;

/** Roles de la plataforma (seccion 33). Se mapean desde el claim "roles" del JWT con prefijo ROLE_. */
public final class Roles {
    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String TENANT_ADMIN = "TENANT_ADMIN";
    public static final String FISCAL_ADMIN = "FISCAL_ADMIN";
    public static final String ACCOUNTANT = "ACCOUNTANT";
    public static final String SELLER = "SELLER";
    public static final String AUDITOR = "AUDITOR";
    public static final String API_CLIENT = "API_CLIENT";

    /** Expresiones para @PreAuthorize. SUPER_ADMIN es de plataforma: NO accede a datos de tenants. */
    public static final String CAN_ADMINISTER = "hasAnyRole('TENANT_ADMIN','FISCAL_ADMIN')";
    public static final String CAN_READ = "hasAnyRole('TENANT_ADMIN','FISCAL_ADMIN','ACCOUNTANT','AUDITOR','SELLER','API_CLIENT')";
    public static final String CAN_AUDIT = "hasAnyRole('AUDITOR','TENANT_ADMIN')";
    public static final String PLATFORM = "hasRole('SUPER_ADMIN')";

    private Roles() {}
}
