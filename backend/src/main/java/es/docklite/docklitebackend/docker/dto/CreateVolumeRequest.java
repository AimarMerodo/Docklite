package es.docklite.docklitebackend.docker.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateVolumeRequest(
        @NotBlank String name,
        String driver
) {
    public String driverOrDefault() {
        return (driver == null || driver.isBlank()) ? "local" : driver;
    }
}
