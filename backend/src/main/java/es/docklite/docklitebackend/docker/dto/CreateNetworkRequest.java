package es.docklite.docklitebackend.docker.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateNetworkRequest(
        @NotBlank String name,
        String driver
) {
    public String driverOrDefault() {
        return (driver == null || driver.isBlank()) ? "bridge" : driver;
    }
}
