package es.docklite.docklitebackend.common.exception;

/**
 * Thrown when an attempt is made to create a Docker resource (volume,
 * network, ...) whose name is already in use on the daemon. Mapped to
 * HTTP 409 Conflict by {@code GlobalExceptionHandler}.
 */
public class ResourceAlreadyExistsException extends RuntimeException {
    public ResourceAlreadyExistsException(String message) {
        super(message);
    }
}
