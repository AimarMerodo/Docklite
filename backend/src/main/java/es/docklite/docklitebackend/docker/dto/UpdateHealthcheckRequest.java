package es.docklite.docklitebackend.docker.dto;

import jakarta.validation.constraints.Min;

import java.util.List;

/**
 * Body for {@code PUT /containers/{id}/healthcheck}. If {@code test} is
 * null or empty, the healthcheck is disabled on recreate. Otherwise the
 * other fields refine the schedule (any null falls back to docker's
 * defaults).
 */
public record UpdateHealthcheckRequest(
        List<String> test,
        @Min(1) Long intervalSeconds,
        @Min(1) Long timeoutSeconds,
        @Min(0) Integer retries,
        @Min(0) Long startPeriodSeconds
) {

    public boolean isDisable() {
        return test == null || test.isEmpty();
    }
}
