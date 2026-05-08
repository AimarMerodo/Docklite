package es.docklite.docklitebackend.docker.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.util.List;

public record CreateContainerRequest(
        @NotBlank String image,
        String name,
        Boolean autoStart,
        String networkId,
        @Valid List<VolumeMount> volumes,
        @Valid List<EnvVar> env,
        @Valid List<PortMapping> ports,

        @Pattern(
                regexp = "^(no|always|on-failure|unless-stopped)$",
                message = "must be one of: no, always, on-failure, unless-stopped"
        )
        String restartPolicy,

        @Min(value = 1, message = "memoryMb must be at least 1")
        Integer memoryMb,

        @DecimalMin(value = "0.1", message = "cpus must be at least 0.1")
        Double cpus
) {
        /** Treats absent / null autoStart as false. */
        public boolean shouldAutoStart() {
                return Boolean.TRUE.equals(autoStart);
        }
}
