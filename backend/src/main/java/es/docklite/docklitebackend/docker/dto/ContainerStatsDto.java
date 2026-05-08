package es.docklite.docklitebackend.docker.dto;

import com.github.dockerjava.api.model.Statistics;

/**
 * Single point-in-time snapshot of a container's CPU + memory usage,
 * meant to be polled by the frontend (~every few seconds) to draw
 * the live charts on the container detail page.
 */
public record ContainerStatsDto(
        String id,
        String timestamp,
        Double cpuPercent,
        Long memoryUsageBytes,
        Long memoryLimitBytes,
        Double memoryPercent
) {

    public static ContainerStatsDto from(String containerId, Statistics current, Statistics previous) {
        return new ContainerStatsDto(
                containerId,
                current.getRead(),
                computeCpuPercent(current, previous),
                memoryUsage(current),
                memoryLimit(current),
                computeMemoryPercent(current)
        );
    }

    /**
     * CPU% computed using the same formula docker stats CLI uses, but
     * across two snapshots taken by the caller (the {@code precpu_stats}
     * field returned by the daemon when {@code stream=false} is queried
     * is empty, so we cannot rely on it):
     *   cpuDelta    = current.totalUsage - previous.totalUsage
     *   systemDelta = current.systemCpuUsage - previous.systemCpuUsage
     *   percent     = (cpuDelta / systemDelta) * onlineCpus * 100
     */
    private static Double computeCpuPercent(Statistics current, Statistics previous) {
        if (current.getCpuStats() == null || previous.getCpuStats() == null) return 0.0;
        var cur = current.getCpuStats();
        var prev = previous.getCpuStats();
        if (cur.getCpuUsage() == null || prev.getCpuUsage() == null) return 0.0;

        Long curTotal = cur.getCpuUsage().getTotalUsage();
        Long prevTotal = prev.getCpuUsage().getTotalUsage();
        Long curSystem = cur.getSystemCpuUsage();
        Long prevSystem = prev.getSystemCpuUsage();
        if (curTotal == null || prevTotal == null || curSystem == null || prevSystem == null) {
            return 0.0;
        }

        long cpuDelta = curTotal - prevTotal;
        long systemDelta = curSystem - prevSystem;
        if (systemDelta <= 0 || cpuDelta < 0) return 0.0;

        long onlineCpus = cur.getOnlineCpus() != null
                ? cur.getOnlineCpus()
                : (cur.getCpuUsage().getPercpuUsage() != null
                        ? cur.getCpuUsage().getPercpuUsage().size()
                        : 1);
        if (onlineCpus <= 0) onlineCpus = 1;

        double pct = ((double) cpuDelta / systemDelta) * onlineCpus * 100.0;
        return Math.round(pct * 100.0) / 100.0;
    }

    private static Long memoryUsage(Statistics s) {
        return s.getMemoryStats() != null ? s.getMemoryStats().getUsage() : null;
    }

    private static Long memoryLimit(Statistics s) {
        return s.getMemoryStats() != null ? s.getMemoryStats().getLimit() : null;
    }

    private static Double computeMemoryPercent(Statistics s) {
        Long usage = memoryUsage(s);
        Long limit = memoryLimit(s);
        if (usage == null || limit == null || limit <= 0) return 0.0;
        double pct = (usage.doubleValue() / limit) * 100.0;
        return Math.round(pct * 100.0) / 100.0;
    }
}
