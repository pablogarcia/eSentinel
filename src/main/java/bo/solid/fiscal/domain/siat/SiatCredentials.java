package bo.solid.fiscal.domain.siat;

import bo.solid.fiscal.domain.org.Environment;
import bo.solid.fiscal.domain.org.Modality;

/** Datos para autenticarse ante el SIAT en nombre de un contribuyente. toString NO expone el token. */
public record SiatCredentials(Environment environment, long nit, String systemCode, Modality modality, String token) {

    @Override
    public String toString() {
        return "SiatCredentials[environment=" + environment + ", nit=" + nit + ", systemCode=" + systemCode
                + ", modality=" + modality + ", token=***]";
    }
}
