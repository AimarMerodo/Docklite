package es.docklite.docklitebackend.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
        String timestamp,
        int status,
        String error,
        String message,
        Map<String, String> fields
) {
    public static ErrorResponse of(HttpStatus status, String message) {
        return new ErrorResponse(
                Instant.now().toString(),
                status.value(),
                status.getReasonPhrase(),
                message,
                null
        );
    }

    public static ErrorResponse of(HttpStatus status, String message, Map<String, String> fields) {
        return new ErrorResponse(
                Instant.now().toString(),
                status.value(),
                status.getReasonPhrase(),
                message,
                fields
        );
    }
}
