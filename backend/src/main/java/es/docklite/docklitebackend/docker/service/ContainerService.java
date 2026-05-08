package es.docklite.docklitebackend.docker.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.DockerClientException;
import com.github.dockerjava.api.exception.DockerException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ExposedPort;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HealthCheck;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.InternetProtocol;
import com.github.dockerjava.api.model.Ports;
import com.github.dockerjava.api.model.RestartPolicy;
import com.github.dockerjava.api.model.Statistics;
import com.github.dockerjava.api.model.Volume;
import es.docklite.docklitebackend.audit.entity.ActivityAction;
import es.docklite.docklitebackend.audit.service.ActivityLogService;
import es.docklite.docklitebackend.common.exception.DockerOperationException;
import es.docklite.docklitebackend.common.exception.InvalidContainerStateException;
import es.docklite.docklitebackend.common.exception.SecurityMessages;
import es.docklite.docklitebackend.docker.dto.ContainerDetailDto;
import es.docklite.docklitebackend.docker.dto.ContainerDto;
import es.docklite.docklitebackend.docker.dto.ContainerStatsDto;
import es.docklite.docklitebackend.docker.dto.CreateContainerRequest;
import es.docklite.docklitebackend.docker.dto.EnvVar;
import es.docklite.docklitebackend.docker.dto.PortInUseDto;
import es.docklite.docklitebackend.docker.dto.PortMapping;
import es.docklite.docklitebackend.docker.dto.RenameContainerRequest;
import es.docklite.docklitebackend.docker.dto.UpdateCommandRequest;
import es.docklite.docklitebackend.docker.dto.UpdateEnvRequest;
import es.docklite.docklitebackend.docker.dto.UpdateHealthcheckRequest;
import es.docklite.docklitebackend.docker.dto.UpdateMountsRequest;
import es.docklite.docklitebackend.docker.dto.UpdatePortsRequest;
import es.docklite.docklitebackend.docker.dto.UpdateResourcesRequest;
import es.docklite.docklitebackend.docker.dto.VolumeMount;
import es.docklite.docklitebackend.docker.entity.ResourceType;
import es.docklite.docklitebackend.user.entity.Role;
import es.docklite.docklitebackend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

@Service
@RequiredArgsConstructor
public class ContainerService {

    private final DockerClient dockerClient;
    private final ResourceOwnershipService ownershipService;
    private final ActivityLogService activityLogService;

    /**
     * Lists every host port currently bound by any container on the daemon
     * — regardless of who owns it, because port conflicts are at machine
     * level. The container name is only revealed when the requesting user
     * owns it (or is admin); otherwise {@code containerName} is null so we
     * do not leak info about other users' workloads.
     */
    public List<PortInUseDto> portsInUse(User currentUser) {
        List<Container> all = dockerClient.listContainersCmd().withShowAll(true).exec();
        // Dedup by (hostPort, protocol): docker reports IPv4 (0.0.0.0) and
        // IPv6 ([::]) bindings as separate entries for the same logical
        // mapping, but the frontend only cares about the uniqueness key.
        java.util.Set<String> seen = new java.util.HashSet<>();
        List<PortInUseDto> out = new ArrayList<>();
        for (Container c : all) {
            if (c.getPorts() == null) continue;
            String name = null;
            if (ownershipService.hasAccess(c.getId(), ResourceType.CONTAINER, currentUser)) {
                name = (c.getNames() != null && c.getNames().length > 0)
                        ? c.getNames()[0].replaceFirst("^/", "")
                        : null;
            }
            for (var p : c.getPorts()) {
                if (p == null || p.getPublicPort() == null) continue;
                String proto = p.getType() != null ? p.getType().toLowerCase() : "tcp";
                String key = p.getPublicPort() + "/" + proto;
                if (!seen.add(key)) continue;
                out.add(new PortInUseDto(p.getPublicPort(), proto, name));
            }
        }
        return out;
    }

    public List<ContainerDto> list(User currentUser, boolean showAll) {
        List<Container> all = dockerClient.listContainersCmd()
                .withShowAll(showAll)
                .exec();

        if (currentUser.getRole() == Role.ADMIN) {
            return all.stream().map(ContainerDto::from).toList();
        }

        List<String> ownedIds = ownershipService.getResourceIds(currentUser.getId(), ResourceType.CONTAINER);
        return all.stream()
                .filter(c -> ownedIds.contains(c.getId()))
                .map(ContainerDto::from)
                .toList();
    }

    public ContainerDetailDto create(CreateContainerRequest req, User currentUser) {
        ensureImageAvailable(req.image(), currentUser);

        CreateContainerCmd cmd = dockerClient.createContainerCmd(req.image());
        if (req.name() != null && !req.name().isBlank()) {
            cmd = cmd.withName(req.name());
        }

        HostConfig hostConfig = HostConfig.newHostConfig();
        boolean hostConfigUsed = false;

        if (req.networkId() != null && !req.networkId().isBlank()) {
            checkNetworkAccess(req.networkId(), currentUser);
            hostConfig = hostConfig.withNetworkMode(req.networkId());
            hostConfigUsed = true;
        }

        if (req.volumes() != null && !req.volumes().isEmpty()) {
            List<Bind> binds = new ArrayList<>();
            for (VolumeMount mount : req.volumes()) {
                if (!ownershipService.hasAccess(mount.volumeName(), ResourceType.VOLUME, currentUser)) {
                    throw new AccessDeniedException(SecurityMessages.ACCESS_DENIED);
                }
                Volume target = new Volume(mount.containerPath());
                AccessMode mode = mount.readOnly() ? AccessMode.ro : AccessMode.rw;
                binds.add(new Bind(mount.volumeName(), target, mode));
            }
            hostConfig = hostConfig.withBinds(binds);
            hostConfigUsed = true;
        }

        if (req.env() != null && !req.env().isEmpty()) {
            List<String> envList = req.env().stream()
                    .map(e -> e.name() + "=" + (e.value() == null ? "" : e.value()))
                    .toList();
            cmd = cmd.withEnv(envList);
        }

        if (req.ports() != null && !req.ports().isEmpty()) {
            List<ExposedPort> exposedPorts = new ArrayList<>();
            Ports portBindings = new Ports();
            for (PortMapping pm : req.ports()) {
                InternetProtocol proto = pm.isUdp() ? InternetProtocol.UDP : InternetProtocol.TCP;
                ExposedPort exposed = new ExposedPort(pm.containerPort(), proto);
                exposedPorts.add(exposed);
                portBindings.bind(exposed, Ports.Binding.bindPort(pm.hostPort()));
            }
            cmd = cmd.withExposedPorts(exposedPorts);
            hostConfig = hostConfig.withPortBindings(portBindings);
            hostConfigUsed = true;
        }

        if (req.restartPolicy() != null && !req.restartPolicy().isBlank()) {
            hostConfig = hostConfig.withRestartPolicy(buildRestartPolicy(req.restartPolicy()));
            hostConfigUsed = true;
        }

        if (req.memoryMb() != null) {
            hostConfig = hostConfig.withMemory(req.memoryMb().longValue() * 1024L * 1024L);
            hostConfigUsed = true;
        }

        if (req.cpus() != null) {
            // Use cpu-period+cpu-quota (not nano-cpus) so subsequent live
            // updates via UpdateContainerCmd are compatible — docker rejects
            // mixing the two CPU representations on the same container.
            long period = 100_000L;
            long quota = (long) (req.cpus() * period);
            hostConfig = hostConfig.withCpuPeriod(period).withCpuQuota(quota);
            hostConfigUsed = true;
        }

        if (hostConfigUsed) {
            cmd = cmd.withHostConfig(hostConfig);
        }

        CreateContainerResponse response = cmd.exec();
        String containerId = response.getId();

        ownershipService.register(containerId, ResourceType.CONTAINER, req.name(), currentUser.getId());
        activityLogService.log(currentUser.getId(), containerId, ResourceType.CONTAINER, ActivityAction.CREATE);

        if (req.shouldAutoStart()) {
            dockerClient.startContainerCmd(containerId).exec();
            activityLogService.log(currentUser.getId(), containerId, ResourceType.CONTAINER, ActivityAction.START);
        }

        return inspect(containerId, currentUser);
    }

    public ContainerDetailDto inspect(String id, User currentUser) {
        var info = dockerClient.inspectContainerCmd(id).exec();
        checkAccess(info.getId(), currentUser);
        return ContainerDetailDto.from(info);
    }

    public void start(String id, User user) {
        String fullId = resolveFullId(id);
        checkAccess(fullId, user);
        dockerClient.startContainerCmd(fullId).exec();
        activityLogService.log(user.getId(), fullId, ResourceType.CONTAINER, ActivityAction.START);
    }

    public void stop(String id, User user) {
        String fullId = resolveFullId(id);
        checkAccess(fullId, user);
        dockerClient.stopContainerCmd(fullId).exec();
        activityLogService.log(user.getId(), fullId, ResourceType.CONTAINER, ActivityAction.STOP);
    }

    public void restart(String id, User user) {
        String fullId = resolveFullId(id);
        checkAccess(fullId, user);
        dockerClient.restartContainerCmd(fullId).exec();
        activityLogService.log(user.getId(), fullId, ResourceType.CONTAINER, ActivityAction.RESTART);
    }

    /**
     * Replaces the volume-mount list of an existing container.
     * <p>
     * Docker does not allow modifying mounts in place: the container must be
     * removed and recreated with the same configuration plus the new mounts.
     * For atomicity we require the container to be stopped (HTTP 409
     * otherwise); the recreated container is left stopped and the caller can
     * start it again if desired. The container's name is preserved; its
     * Docker id changes.
     */
    public ContainerDetailDto updateMounts(String id, UpdateMountsRequest req, User currentUser) {
        String fullId = resolveFullId(id);
        checkAccess(fullId, currentUser);

        var info = dockerClient.inspectContainerCmd(fullId).exec();

        if (Boolean.TRUE.equals(info.getState() != null ? info.getState().getRunning() : null)) {
            throw new InvalidContainerStateException(
                    "Container must be stopped before modifying mounts");
        }

        // Validate ownership of every volume in the new list before touching anything
        if (req.mounts() != null) {
            for (VolumeMount m : req.mounts()) {
                if (!ownershipService.hasAccess(m.volumeName(), ResourceType.VOLUME, currentUser)) {
                    throw new AccessDeniedException(SecurityMessages.ACCESS_DENIED);
                }
            }
        }

        String name = info.getName() != null ? info.getName().replaceFirst("^/", "") : null;
        String image = info.getConfig() != null ? info.getConfig().getImage() : null;

        // Remove the old container; ownership row + audit follow the same path as DELETE
        dockerClient.removeContainerCmd(fullId).withForce(true).exec();
        ownershipService.unregister(fullId, ResourceType.CONTAINER);

        String newId;
        try {
            newId = recreate(info, name, image, req.mounts(), null, null, null, null, null);
        } catch (RuntimeException ex) {
            throw new DockerOperationException(
                    "Container recreation failed; container '" + name + "' is no longer available: " + ex.getMessage(),
                    ex);
        }

        ownershipService.register(newId, ResourceType.CONTAINER, name, currentUser.getId());
        activityLogService.log(currentUser.getId(), newId, ResourceType.CONTAINER, ActivityAction.UPDATE_MOUNTS);

        return ContainerDetailDto.from(dockerClient.inspectContainerCmd(newId).exec());
    }

    /**
     * Replaces the published-port list of an existing container, preserving
     * everything else (mounts, env, network, restart policy, etc.). Same
     * stop-required + recreate semantics as {@link #updateMounts}.
     */
    public ContainerDetailDto updatePorts(String id, UpdatePortsRequest req, User currentUser) {
        String fullId = resolveFullId(id);
        checkAccess(fullId, currentUser);

        var info = dockerClient.inspectContainerCmd(fullId).exec();

        if (Boolean.TRUE.equals(info.getState() != null ? info.getState().getRunning() : null)) {
            throw new InvalidContainerStateException(
                    "Container must be stopped before modifying ports");
        }

        String name = info.getName() != null ? info.getName().replaceFirst("^/", "") : null;
        String image = info.getConfig() != null ? info.getConfig().getImage() : null;

        dockerClient.removeContainerCmd(fullId).withForce(true).exec();
        ownershipService.unregister(fullId, ResourceType.CONTAINER);

        String newId;
        try {
            newId = recreate(info, name, image, null, req.ports(), null, null, null, null);
        } catch (RuntimeException ex) {
            throw new DockerOperationException(
                    "Container recreation failed; container '" + name + "' is no longer available: " + ex.getMessage(),
                    ex);
        }

        ownershipService.register(newId, ResourceType.CONTAINER, name, currentUser.getId());
        activityLogService.log(currentUser.getId(), newId, ResourceType.CONTAINER, ActivityAction.UPDATE_PORTS);

        return ContainerDetailDto.from(dockerClient.inspectContainerCmd(newId).exec());
    }

    /** Replaces the env-var list of an existing container (recreate-style). */
    public ContainerDetailDto updateEnv(String id, UpdateEnvRequest req, User currentUser) {
        return doRecreate(id, currentUser, ActivityAction.UPDATE_ENV,
                "Container must be stopped before modifying environment",
                (info, name, image) -> recreate(info, name, image, null, null, req.env(), null, null, null));
    }

    /** Replaces command and/or entrypoint of an existing container (recreate-style). */
    public ContainerDetailDto updateCommand(String id, UpdateCommandRequest req, User currentUser) {
        return doRecreate(id, currentUser, ActivityAction.UPDATE_COMMAND,
                "Container must be stopped before modifying command",
                (info, name, image) -> recreate(info, name, image, null, null, null, req, null, null));
    }

    /** Replaces (or disables) the healthcheck of an existing container (recreate-style). */
    public ContainerDetailDto updateHealthcheck(String id, UpdateHealthcheckRequest req, User currentUser) {
        return doRecreate(id, currentUser, ActivityAction.UPDATE_HEALTHCHECK,
                "Container must be stopped before modifying healthcheck",
                (info, name, image) -> recreate(info, name, image, null, null, null, null, req, null));
    }

    /**
     * Updates memory limit, CPU limit and/or restart policy.
     *
     * <p>Memory and CPUs are applied <strong>live</strong> via
     * {@code docker update} (id preserved). Restart policy goes through
     * a <strong>recreate</strong> because docker-java 3.4.1 does not
     * expose {@code withRestartPolicy} on {@code UpdateContainerCmd} —
     * the container must be stopped and the id changes.
     */
    public ContainerDetailDto updateResources(String id, UpdateResourcesRequest req, User currentUser) {
        String fullId = resolveFullId(id);
        checkAccess(fullId, currentUser);

        if (req.memoryMb() == null && req.cpus() == null && req.restartPolicy() == null) {
            throw new IllegalArgumentException(
                    "At least one of memoryMb, cpus or restartPolicy must be provided");
        }

        // Step 1 — live changes (memory / cpus). Skipped if neither is set.
        if (req.hasLiveChanges()) {
            var cmd = dockerClient.updateContainerCmd(fullId);
            if (req.memoryMb() != null) {
                long bytes = req.memoryMb().longValue() * 1024L * 1024L;
                cmd = cmd.withMemory(bytes);
                // Docker rejects a memory update unless memorySwap is also
                // supplied (or already set). Use -1 for unlimited swap so
                // the daemon picks its own default.
                cmd = cmd.withMemorySwap(-1L);
            }
            if (req.cpus() != null) {
                // docker-java's UpdateContainerCmd has no withNanoCPUs;
                // emulate via cpu-period + cpu-quota (what the daemon does
                // internally when --cpus is given).
                long period = 100_000L;
                long quota = (long) (req.cpus() * period);
                cmd = cmd.withCpuPeriod(period).withCpuQuota(quota);
            }
            cmd.exec();
            activityLogService.log(currentUser.getId(), fullId, ResourceType.CONTAINER,
                    ActivityAction.UPDATE_RESOURCES);
        }

        // Step 2 — restart policy via recreate (only path supported by the
        // current docker-java version). Throws 409 if container is running.
        if (req.restartPolicy() != null) {
            return doRecreate(fullId, currentUser, ActivityAction.UPDATE_RESOURCES,
                    "Container must be stopped before changing restart policy",
                    (info, name, image) -> recreate(info, name, image,
                            null, null, null, null, null, req.restartPolicy()));
        }

        return ContainerDetailDto.from(dockerClient.inspectContainerCmd(fullId).exec());
    }

    /**
     * Live rename of a container — uses {@code docker rename}, so the
     * container id stays the same. The {@code resource_name} stored in
     * the ownership table is also updated.
     */
    public ContainerDetailDto rename(String id, RenameContainerRequest req, User currentUser) {
        String fullId = resolveFullId(id);
        checkAccess(fullId, currentUser);
        dockerClient.renameContainerCmd(fullId).withName(req.name()).exec();
        ownershipService.renameResource(fullId, ResourceType.CONTAINER, req.name());
        activityLogService.log(currentUser.getId(), fullId, ResourceType.CONTAINER, ActivityAction.RENAME);
        return ContainerDetailDto.from(dockerClient.inspectContainerCmd(fullId).exec());
    }

    @FunctionalInterface
    private interface RecreateFn {
        String apply(com.github.dockerjava.api.command.InspectContainerResponse info,
                     String name, String image);
    }

    /** Shared scaffolding for stop-required → rm → recreate operations. */
    private ContainerDetailDto doRecreate(String id, User currentUser, ActivityAction action,
                                          String stopMessage, RecreateFn fn) {
        String fullId = resolveFullId(id);
        checkAccess(fullId, currentUser);

        var info = dockerClient.inspectContainerCmd(fullId).exec();
        if (Boolean.TRUE.equals(info.getState() != null ? info.getState().getRunning() : null)) {
            throw new InvalidContainerStateException(stopMessage);
        }

        String name = info.getName() != null ? info.getName().replaceFirst("^/", "") : null;
        String image = info.getConfig() != null ? info.getConfig().getImage() : null;

        dockerClient.removeContainerCmd(fullId).withForce(true).exec();
        ownershipService.unregister(fullId, ResourceType.CONTAINER);

        String newId;
        try {
            newId = fn.apply(info, name, image);
        } catch (RuntimeException ex) {
            throw new DockerOperationException(
                    "Container recreation failed; container '" + name + "' is no longer available: " + ex.getMessage(),
                    ex);
        }

        ownershipService.register(newId, ResourceType.CONTAINER, name, currentUser.getId());
        activityLogService.log(currentUser.getId(), newId, ResourceType.CONTAINER, action);

        return ContainerDetailDto.from(dockerClient.inspectContainerCmd(newId).exec());
    }

    /**
     * Recreates a container with the same configuration as {@code old},
     * optionally replacing volume mounts, port bindings, env, command,
     * healthcheck or restart policy. Anything left {@code null} is
     * preserved from the original.
     */
    private String recreate(
            com.github.dockerjava.api.command.InspectContainerResponse old,
            String name,
            String image,
            List<VolumeMount> newMounts,
            List<PortMapping> newPorts,
            List<EnvVar> newEnv,
            UpdateCommandRequest newCommand,
            UpdateHealthcheckRequest newHealthcheck,
            String newRestartPolicy) {
        var cmd = dockerClient.createContainerCmd(image);
        if (name != null && !name.isBlank()) {
            cmd = cmd.withName(name);
        }

        var oldConfig = old.getConfig();
        if (oldConfig != null) {
            // Cmd / Entrypoint
            if (newCommand != null) {
                if (newCommand.entrypoint() != null && !newCommand.entrypoint().isEmpty()) {
                    cmd = cmd.withEntrypoint(newCommand.entrypoint());
                }
                if (newCommand.command() != null && !newCommand.command().isEmpty()) {
                    cmd = cmd.withCmd(newCommand.command());
                }
                // a null/empty field means "revert to image default" — we
                // simply don't set it, and docker falls back to the image
            } else {
                if (oldConfig.getCmd() != null) cmd = cmd.withCmd(oldConfig.getCmd());
                if (oldConfig.getEntrypoint() != null) cmd = cmd.withEntrypoint(oldConfig.getEntrypoint());
            }

            // Env
            if (newEnv != null) {
                List<String> envList = newEnv.stream()
                        .map(e -> e.name() + "=" + (e.value() == null ? "" : e.value()))
                        .toList();
                cmd = cmd.withEnv(envList);
            } else if (oldConfig.getEnv() != null) {
                cmd = cmd.withEnv(oldConfig.getEnv());
            }

            // Always preserved
            if (oldConfig.getWorkingDir() != null) cmd = cmd.withWorkingDir(oldConfig.getWorkingDir());
            if (oldConfig.getUser() != null) cmd = cmd.withUser(oldConfig.getUser());
            if (oldConfig.getLabels() != null) cmd = cmd.withLabels(oldConfig.getLabels());

            // Healthcheck
            if (newHealthcheck != null) {
                if (newHealthcheck.isDisable()) {
                    cmd = cmd.withHealthcheck(new HealthCheck().withTest(List.of("NONE")));
                } else {
                    HealthCheck hc = new HealthCheck().withTest(newHealthcheck.test());
                    if (newHealthcheck.intervalSeconds() != null)
                        hc = hc.withInterval(newHealthcheck.intervalSeconds() * 1_000_000_000L);
                    if (newHealthcheck.timeoutSeconds() != null)
                        hc = hc.withTimeout(newHealthcheck.timeoutSeconds() * 1_000_000_000L);
                    if (newHealthcheck.retries() != null)
                        hc = hc.withRetries(newHealthcheck.retries());
                    if (newHealthcheck.startPeriodSeconds() != null)
                        hc = hc.withStartPeriod(newHealthcheck.startPeriodSeconds() * 1_000_000_000L);
                    cmd = cmd.withHealthcheck(hc);
                }
            } else if (oldConfig.getHealthcheck() != null) {
                cmd = cmd.withHealthcheck(oldConfig.getHealthcheck());
            }
        }

        HostConfig newHostConfig = HostConfig.newHostConfig();
        var oldHc = old.getHostConfig();
        if (oldHc != null) {
            if (oldHc.getNetworkMode() != null) newHostConfig = newHostConfig.withNetworkMode(oldHc.getNetworkMode());
            if (newRestartPolicy != null) {
                newHostConfig = newHostConfig.withRestartPolicy(buildRestartPolicy(newRestartPolicy));
            } else if (oldHc.getRestartPolicy() != null) {
                newHostConfig = newHostConfig.withRestartPolicy(oldHc.getRestartPolicy());
            }
            if (oldHc.getMemory() != null && oldHc.getMemory() > 0) newHostConfig = newHostConfig.withMemory(oldHc.getMemory());
            // Preserve cpu limits — prefer cpu-period/quota (current scheme),
            // fall back to nano-cpus for containers created before the
            // switch.
            if (oldHc.getCpuPeriod() != null && oldHc.getCpuPeriod() > 0
                    && oldHc.getCpuQuota() != null && oldHc.getCpuQuota() > 0) {
                newHostConfig = newHostConfig
                        .withCpuPeriod(oldHc.getCpuPeriod().longValue())
                        .withCpuQuota(oldHc.getCpuQuota().longValue());
            } else if (oldHc.getNanoCPUs() != null && oldHc.getNanoCPUs() > 0) {
                newHostConfig = newHostConfig.withNanoCPUs(oldHc.getNanoCPUs());
            }
        }

        // ---- Ports ----
        if (newPorts != null) {
            // Reconstruct ExposedPorts from scratch using the image's
            // Dockerfile-EXPOSE list (the canonical source) plus the ports
            // being newly bound. We do NOT copy oldConfig.getExposedPorts()
            // because that field accumulates entries across PUT calls and
            // never shrinks, leaving orphaned chips when the user removes
            // a binding it had added previously.
            List<ExposedPort> exposed = new ArrayList<>();
            try {
                var imageInspect = dockerClient.inspectImageCmd(image).exec();
                if (imageInspect.getConfig() != null && imageInspect.getConfig().getExposedPorts() != null) {
                    for (ExposedPort ep : imageInspect.getConfig().getExposedPorts()) {
                        if (ep != null) exposed.add(ep);
                    }
                }
            } catch (RuntimeException ignored) {
                // image gone or unreadable — fall through with the new
                // bindings only; the recreate will fail later with a
                // clearer error if the image is truly missing
            }
            Ports portBindings = new Ports();
            for (PortMapping pm : newPorts) {
                InternetProtocol proto = pm.isUdp() ? InternetProtocol.UDP : InternetProtocol.TCP;
                ExposedPort ep = new ExposedPort(pm.containerPort(), proto);
                if (!exposed.contains(ep)) exposed.add(ep);
                portBindings.bind(ep, Ports.Binding.bindPort(pm.hostPort()));
            }
            cmd = cmd.withExposedPorts(exposed);
            newHostConfig = newHostConfig.withPortBindings(portBindings);
        } else {
            if (oldConfig != null && oldConfig.getExposedPorts() != null) {
                cmd = cmd.withExposedPorts(oldConfig.getExposedPorts());
            }
            if (oldHc != null && oldHc.getPortBindings() != null) {
                newHostConfig = newHostConfig.withPortBindings(oldHc.getPortBindings());
            }
        }

        // ---- Mounts ----
        List<Bind> binds = new ArrayList<>();
        if (old.getMounts() != null) {
            for (var m : old.getMounts()) {
                if (m == null || m.getSource() == null || m.getDestination() == null) continue;
                boolean hasName = m.getName() != null && !m.getName().isBlank();
                boolean isBind = !hasName;
                if (newMounts != null && !isBind) {
                    // Replacing volumes: skip original volume mounts (only binds preserved)
                    continue;
                }
                Volume target = new Volume(m.getDestination().getPath());
                AccessMode mode = Boolean.FALSE.equals(m.getRW()) ? AccessMode.ro : AccessMode.rw;
                String source = hasName ? m.getName() : m.getSource();
                binds.add(new Bind(source, target, mode));
            }
        }
        if (newMounts != null) {
            for (VolumeMount m : newMounts) {
                Volume target = new Volume(m.containerPath());
                AccessMode mode = m.readOnly() ? AccessMode.ro : AccessMode.rw;
                binds.add(new Bind(m.volumeName(), target, mode));
            }
        }
        if (!binds.isEmpty()) {
            newHostConfig = newHostConfig.withBinds(binds);
        }

        cmd = cmd.withHostConfig(newHostConfig);
        return cmd.exec().getId();
    }

    public void remove(String id, User user) {
        String fullId = resolveFullId(id);
        checkAccess(fullId, user);
        dockerClient.removeContainerCmd(fullId).withForce(true).exec();
        ownershipService.unregister(fullId, ResourceType.CONTAINER);
        activityLogService.log(user.getId(), fullId, ResourceType.CONTAINER, ActivityAction.DELETE);
    }

    /**
     * Stale tolerance for cached stats samples — anything older than this
     * is considered too out of date to be used as the "previous" snapshot
     * for CPU% computation, so we take a fresh pair instead.
     */
    private static final long STATS_CACHE_MAX_AGE_MS = 5000;
    private static final long STATS_SAMPLE_GAP_MS = 500;

    /**
     * Per-container cache of the last stats sample. Lets a polling client
     * get a CPU% with a single docker call (~1.5s) instead of two
     * back-to-back samples (~3.5s) on every poll.
     */
    private static final java.util.Map<String, CachedSample> STATS_CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    private record CachedSample(Statistics stats, long takenAtMs) {}

    public ContainerStatsDto stats(String id, User user) {
        String fullId = resolveFullId(id);
        checkAccess(fullId, user);

        long now = System.currentTimeMillis();
        CachedSample previous = STATS_CACHE.get(fullId);
        boolean cacheUsable = previous != null
                && (now - previous.takenAtMs) <= STATS_CACHE_MAX_AGE_MS;

        Statistics first;
        if (cacheUsable) {
            first = previous.stats;
        } else {
            first = sampleStatsOnce(fullId);
            try {
                Thread.sleep(STATS_SAMPLE_GAP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DockerOperationException("Stats sampling interrupted", e);
            }
        }

        Statistics second = sampleStatsOnce(fullId);
        STATS_CACHE.put(fullId, new CachedSample(second, System.currentTimeMillis()));
        return ContainerStatsDto.from(fullId, second, first);
    }

    private Statistics sampleStatsOnce(String fullId) {
        AtomicReference<Statistics> ref = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);
        try (var callback = dockerClient.statsCmd(fullId)
                .withNoStream(true)
                .exec(new ResultCallback.Adapter<Statistics>() {
                    @Override
                    public void onNext(Statistics s) {
                        ref.compareAndSet(null, s);
                        latch.countDown();
                    }
                })) {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new DockerOperationException("Stats request timed out");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DockerOperationException("Stats request interrupted", e);
        } catch (java.io.IOException e) {
            throw new DockerOperationException("Stats request failed", e);
        }
        Statistics stats = ref.get();
        if (stats == null) {
            throw new DockerOperationException("No stats sample received");
        }
        return stats;
    }

    public String logs(String id, User user, int tail) {
        String fullId = resolveFullId(id);
        checkAccess(fullId, user);
        StringBuilder sb = new StringBuilder();
        try {
            dockerClient.logContainerCmd(fullId)
                    .withStdOut(true)
                    .withStdErr(true)
                    .withTail(tail)
                    .exec(new ResultCallback.Adapter<Frame>() {
                        @Override
                        public void onNext(Frame frame) {
                            sb.append(new String(frame.getPayload()));
                        }
                    }).awaitCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return sb.toString();
    }

    private String resolveFullId(String id) {
        return dockerClient.inspectContainerCmd(id).exec().getId();
    }

    private static final long PULL_TIMEOUT_MINUTES = 2;

    /** Si la imagen no está en local, la descarga (pull-on-create). */
    private void ensureImageAvailable(String imageRef, User currentUser) {
        try {
            dockerClient.inspectImageCmd(imageRef).exec();
            return;
        } catch (NotFoundException notLocal) {
            // not in local cache, fall through to pull
        }

        String repo = imageRef;
        String tag = "latest";
        int colon = imageRef.lastIndexOf(':');
        if (colon > 0 && imageRef.indexOf('/', colon) == -1) {
            repo = imageRef.substring(0, colon);
            tag = imageRef.substring(colon + 1);
        }

        try {
            boolean completed = dockerClient.pullImageCmd(repo)
                    .withTag(tag)
                    .exec(new PullImageResultCallback())
                    .awaitCompletion(PULL_TIMEOUT_MINUTES, TimeUnit.MINUTES);
            if (!completed) {
                throw new DockerOperationException("Image pull timed out for " + imageRef);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DockerOperationException("Pull interrupted", e);
        } catch (DockerException | DockerClientException e) {
            // unauthorized, manifest unknown, image not found in registry,
            // registry refused, etc. Both docker-java exception hierarchies
            // are caught here to surface a clean 400 to the client.
            throw new IllegalArgumentException("Could not pull image '" + imageRef + "': " + e.getMessage());
        }

        String fullRef = repo + ":" + tag;
        String pulledId;
        try {
            pulledId = dockerClient.inspectImageCmd(fullRef).exec().getId();
        } catch (NotFoundException ex) {
            // pull "completed" silently but the image is still not on the daemon
            // — typically the reference does not exist in the registry
            throw new IllegalArgumentException("Image '" + imageRef + "' could not be pulled");
        }
        try {
            ownershipService.register(pulledId, ResourceType.IMAGE, fullRef, currentUser.getId());
        } catch (org.springframework.dao.DataIntegrityViolationException ignored) {
            // ya estaba registrada para este user
        }
        activityLogService.log(currentUser.getId(), pulledId, ResourceType.IMAGE, ActivityAction.PULL);
    }

    private void checkAccess(String containerId, User user) {
        if (!ownershipService.hasAccess(containerId, ResourceType.CONTAINER, user)) {
            throw new AccessDeniedException(SecurityMessages.ACCESS_DENIED);
        }
    }

    private RestartPolicy buildRestartPolicy(String policy) {
        return switch (policy.toLowerCase()) {
            case "always" -> RestartPolicy.alwaysRestart();
            case "on-failure" -> RestartPolicy.onFailureRestart(0);
            case "unless-stopped" -> RestartPolicy.unlessStoppedRestart();
            default -> RestartPolicy.noRestart();
        };
    }

    private void checkNetworkAccess(String networkRef, User user) {
        var info = dockerClient.inspectNetworkCmd().withNetworkId(networkRef).exec();
        if (List.of("bridge", "host", "none").contains(info.getName())) {
            return; // default networks are open to everyone
        }
        if (!ownershipService.hasAccess(info.getId(), ResourceType.NETWORK, user)) {
            throw new AccessDeniedException(SecurityMessages.ACCESS_DENIED);
        }
    }
}
