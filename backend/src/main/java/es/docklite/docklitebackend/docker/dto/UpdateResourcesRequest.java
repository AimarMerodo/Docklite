package es.docklite.docklitebackend.docker.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

/**
 * Body for {@code PATCH /containers/{id}/resources}. All fields are
 * optional; a null/missing field means "do not touch".
 *
 * <p>Memory and CPUs are applied <strong>live</strong> via
 * {@code docker update}; the container id is preserved. Restart policy
 * is applied via container <strong>recreate</strong> because
 * docker-java 3.4.1 does not expose {@code withRestartPolicy} on
 * {@code UpdateContainerCmd} — so the container must be stopped before
 * sending {@code restartPolicy}, and the id changes after the call.
 */
public record UpdateResourcesRequest(
        @Min(value = 1, message = "memoryMb must be at least 1")
        Integer memoryMb,

        @DecimalMin(value = "0.1", message = "cpus must be at least 0.1")
        Double cpus,

        @Pattern(
                regexp = "^(no|always|on-failure|unless-stopped)$",
                message = "restartPolicy must be one of: no, always, on-failure, unless-stopped"
        )
        String restartPolicy
) {
    public boolean hasLiveChanges() {
        return memoryMb != null || cpus != null;
    }
}
