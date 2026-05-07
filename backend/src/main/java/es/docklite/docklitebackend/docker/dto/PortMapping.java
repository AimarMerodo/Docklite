package es.docklite.docklitebackend.docker.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record PortMapping(
        @Min(1) @Max(65535) int hostPort,
        @Min(1) @Max(65535) int containerPort,
        String protocol
) {
    public boolean isUdp() {
        return "udp".equalsIgnoreCase(protocol);
    }
}
