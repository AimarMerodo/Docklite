package es.docklite.docklitebackend.common.exception;

/**
 * Centralised constants for messages exposed by the authorization layer.
 * Keeping them in a single place avoids subtle wording drift between
 * the services that throw the exception, the global exception handler
 * and the JSON access-denied handler.
 */
public final class SecurityMessages {

    public static final String ACCESS_DENIED = "Access denied";

    // User-facing (the frontend shows backend messages verbatim), hence Spanish.
    public static final String DEMO_READ_ONLY = "Modo demo: solo lectura";

    private SecurityMessages() {
        // utility class
    }
}
