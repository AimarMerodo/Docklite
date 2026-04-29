package es.docklite.docklitebackend.system.dto;

public record DashboardSummaryDto(
        long totalContainers,
        long running,
        long stopped,
        long totalImages,
        long totalNetworks,
        long totalVolumes
) {
}
