package es.docklite.docklitebackend.docker.dto;

import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.HealthCheck;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.RestartPolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Detailed container view used by {@code GET /containers/{id}}.
 * Built from {@link InspectContainerResponse}; structured into
 * cohesive sub-records (state, config, hostConfig, networks, ports,
 * mounts, healthcheck) so the frontend can render the detail page
 * without having to know docker-java internals.
 */
public record ContainerDetailDto(
        String id,
        String name,
        String image,
        String imageId,
        String createdAt,
        StateInfo state,
        ConfigInfo config,
        HostConfigInfo hostConfig,
        List<NetworkInfo> networks,
        List<PortInfo> ports,
        List<MountInfo> mounts,
        HealthcheckInfo healthcheck
) {

    public record StateInfo(
            String status,
            Boolean running,
            Boolean paused,
            Boolean restarting,
            Long pid,
            Long exitCode,
            String startedAt,
            String finishedAt,
            Integer restartCount
    ) {}

    public record ConfigInfo(
            List<String> command,
            List<String> entrypoint,
            String workingDir,
            String user,
            List<String> env
    ) {}

    public record HostConfigInfo(
            Long memoryLimitBytes,
            Long nanoCpus,
            String restartPolicy,
            String networkMode
    ) {}

    public record NetworkInfo(
            String name,
            String ipAddress,
            String gateway,
            String macAddress
    ) {}

    public record PortInfo(
            Integer privatePort,
            Integer publicPort,
            String protocol
    ) {}

    public record MountInfo(
            String type,
            String source,
            String destination,
            Boolean rw,
            String volumeName
    ) {}

    public record HealthcheckInfo(
            List<String> test,
            Long intervalNanos,
            Long timeoutNanos,
            Integer retries,
            Long startPeriodNanos
    ) {}

    public static ContainerDetailDto from(InspectContainerResponse r) {
        return new ContainerDetailDto(
                r.getId(),
                r.getName() != null ? r.getName().replaceFirst("^/", "") : null,
                r.getConfig() != null ? r.getConfig().getImage() : null,
                r.getImageId(),
                r.getCreated(),
                buildState(r),
                buildConfig(r),
                buildHostConfig(r),
                buildNetworks(r),
                buildPorts(r),
                buildMounts(r),
                buildHealthcheck(r)
        );
    }

    private static StateInfo buildState(InspectContainerResponse r) {
        var s = r.getState();
        if (s == null) {
            return new StateInfo(null, null, null, null, null, null, null, null, r.getRestartCount());
        }
        return new StateInfo(
                s.getStatus(),
                s.getRunning(),
                s.getPaused(),
                s.getRestarting(),
                toLong(s.getPid()),
                toLong(s.getExitCode()),
                s.getStartedAt(),
                s.getFinishedAt(),
                r.getRestartCount()
        );
    }

    private static ConfigInfo buildConfig(InspectContainerResponse r) {
        var c = r.getConfig();
        if (c == null) {
            return new ConfigInfo(List.of(), List.of(), null, null, List.of());
        }
        return new ConfigInfo(
                arrayToList(c.getCmd()),
                arrayToList(c.getEntrypoint()),
                c.getWorkingDir(),
                c.getUser(),
                arrayToList(c.getEnv())
        );
    }

    private static HostConfigInfo buildHostConfig(InspectContainerResponse r) {
        HostConfig hc = r.getHostConfig();
        if (hc == null) {
            return new HostConfigInfo(null, null, null, null);
        }
        RestartPolicy rp = hc.getRestartPolicy();
        return new HostConfigInfo(
                hc.getMemory(),
                effectiveNanoCpus(hc),
                rp != null ? rp.getName() : null,
                hc.getNetworkMode()
        );
    }

    /**
     * Returns the container's CPU limit in nano-cpus, regardless of which
     * underlying scheme was used at create time:
     *  - {@code nano-cpus} directly, when set;
     *  - or computed from {@code cpu-period} / {@code cpu-quota}, the
     *    representation we use now so live updates via
     *    {@code docker update} stay compatible.
     */
    private static Long effectiveNanoCpus(HostConfig hc) {
        if (hc.getNanoCPUs() != null && hc.getNanoCPUs() > 0) {
            return hc.getNanoCPUs();
        }
        Long period = hc.getCpuPeriod() != null ? hc.getCpuPeriod().longValue() : null;
        Long quota = hc.getCpuQuota() != null ? hc.getCpuQuota().longValue() : null;
        if (period != null && period > 0 && quota != null && quota > 0) {
            return Math.round(((double) quota / period) * 1_000_000_000.0);
        }
        return 0L;
    }

    private static List<NetworkInfo> buildNetworks(InspectContainerResponse r) {
        if (r.getNetworkSettings() == null || r.getNetworkSettings().getNetworks() == null) {
            return List.of();
        }
        List<NetworkInfo> out = new ArrayList<>();
        for (Map.Entry<String, ContainerNetwork> e : r.getNetworkSettings().getNetworks().entrySet()) {
            ContainerNetwork n = e.getValue();
            out.add(new NetworkInfo(
                    e.getKey(),
                    n != null ? n.getIpAddress() : null,
                    n != null ? n.getGateway() : null,
                    n != null ? n.getMacAddress() : null
            ));
        }
        return out;
    }

    /**
     * Builds the port list from the persistent configuration, not from the
     * runtime {@code NetworkSettings.Ports} (which is empty when the
     * container is stopped). Sources combined:
     *  - {@code HostConfig.PortBindings}: explicit host:container mappings
     *    declared at create time (carry {@code publicPort}).
     *  - {@code Config.ExposedPorts}: ports the image exposes via Dockerfile
     *    {@code EXPOSE} that are not also mapped (carry
     *    {@code publicPort = null}).
     */
    private static List<PortInfo> buildPorts(InspectContainerResponse r) {
        List<PortInfo> out = new ArrayList<>();
        Set<ExposedPort> seen = new HashSet<>();

        if (r.getHostConfig() != null && r.getHostConfig().getPortBindings() != null) {
            Map<ExposedPort, Ports.Binding[]> bindings =
                    r.getHostConfig().getPortBindings().getBindings();
            if (bindings != null) {
                for (Map.Entry<ExposedPort, Ports.Binding[]> e : bindings.entrySet()) {
                    ExposedPort exposed = e.getKey();
                    if (exposed == null) continue;
                    seen.add(exposed);
                    String proto = exposed.getProtocol() != null ? exposed.getProtocol().toString() : "tcp";
                    Ports.Binding[] binds = e.getValue();
                    if (binds == null || binds.length == 0) {
                        out.add(new PortInfo(exposed.getPort(), null, proto));
                        continue;
                    }
                    for (Ports.Binding b : binds) {
                        Integer publicPort = parsePort(b != null ? b.getHostPortSpec() : null);
                        out.add(new PortInfo(exposed.getPort(), publicPort, proto));
                    }
                }
            }
        }

        if (r.getConfig() != null && r.getConfig().getExposedPorts() != null) {
            for (ExposedPort exposed : r.getConfig().getExposedPorts()) {
                if (exposed == null || seen.contains(exposed)) continue;
                String proto = exposed.getProtocol() != null ? exposed.getProtocol().toString() : "tcp";
                out.add(new PortInfo(exposed.getPort(), null, proto));
            }
        }

        return out;
    }

    private static List<MountInfo> buildMounts(InspectContainerResponse r) {
        if (r.getMounts() == null) {
            return List.of();
        }
        List<MountInfo> out = new ArrayList<>();
        for (var m : r.getMounts()) {
            if (m == null) continue;
            // docker-java's Mount has no explicit "type" field; derive it:
            // named-volume mounts have a non-blank name, bind mounts don't
            boolean hasName = m.getName() != null && !m.getName().isBlank();
            String inferredType = hasName ? "volume" : "bind";
            out.add(new MountInfo(
                    inferredType,
                    m.getSource(),
                    m.getDestination() != null ? m.getDestination().getPath() : null,
                    m.getRW(),
                    hasName ? m.getName() : null
            ));
        }
        return out;
    }

    private static HealthcheckInfo buildHealthcheck(InspectContainerResponse r) {
        if (r.getConfig() == null) return null;
        HealthCheck hc = r.getConfig().getHealthcheck();
        if (hc == null) return null;
        return new HealthcheckInfo(
                hc.getTest() != null ? Collections.unmodifiableList(hc.getTest()) : List.of(),
                hc.getInterval(),
                hc.getTimeout(),
                hc.getRetries(),
                hc.getStartPeriod()
        );
    }

    private static List<String> arrayToList(String[] arr) {
        return arr == null ? List.of() : List.of(arr);
    }

    private static Long toLong(Long v) {
        return v;
    }

    private static Long toLong(Integer v) {
        return v == null ? null : v.longValue();
    }

    private static Integer parsePort(String spec) {
        if (spec == null || spec.isBlank()) return null;
        try {
            return Integer.parseInt(spec);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
