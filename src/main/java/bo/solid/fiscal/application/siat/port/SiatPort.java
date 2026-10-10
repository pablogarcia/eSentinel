package bo.solid.fiscal.application.siat.port;

import bo.solid.fiscal.domain.siat.BranchScope;
import bo.solid.fiscal.domain.siat.CommunicationStatus;
import bo.solid.fiscal.domain.siat.CufdResult;
import bo.solid.fiscal.domain.siat.CuisResult;
import bo.solid.fiscal.domain.siat.SiatCredentials;
import bo.solid.fiscal.domain.siat.SiatDateTime;
import bo.solid.fiscal.domain.siat.SiatScope;

/**
 * Unico punto por el que el dominio/aplicacion hablan con el SIAT. Nada de SOAP fuera de la implementacion.
 * Las fallas se lanzan como FiscalException (siatUnavailable / siatTimeout / siatAuth / siatProtocol).
 * Una implementacion NO reintenta por su cuenta: reintentar es decision de quien conoce la idempotencia.
 */
public interface SiatPort {

    CommunicationStatus verifyCommunication(SiatCredentials credentials);

    SiatDateTime synchronizeDateTime(SiatCredentials credentials, SiatScope scope);

    /** Solicita un CUIS para la sucursal/POS. */
    CuisResult requestCuis(SiatCredentials credentials, BranchScope scope);

    /** Solicita el CUFD del dia para el alcance, con su CUIS vigente. */
    CufdResult requestCufd(SiatCredentials credentials, SiatScope scope);
}
