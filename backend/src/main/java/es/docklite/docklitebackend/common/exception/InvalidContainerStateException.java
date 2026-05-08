package es.docklite.docklitebackend.common.exception;

/**
 * Thrown when an operation requires the container to be in a specific state
 * (e.g. stopped) but it is not. Mapped to HTTP 409 Conflict by
 * {@code GlobalExceptionHandler}.
 */
public class InvalidContainerStateException extends RuntimeException {
    public InvalidContainerStateException(String message) {
        super(message);
    }
}
