package es.docklite.docklitebackend.docker.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateNetworkResponse;
import com.github.dockerjava.api.model.Network;
import es.docklite.docklitebackend.audit.entity.ActivityAction;
import es.docklite.docklitebackend.audit.service.ActivityLogService;
import es.docklite.docklitebackend.common.exception.SecurityMessages;
import es.docklite.docklitebackend.docker.dto.CreateNetworkRequest;
import es.docklite.docklitebackend.docker.dto.NetworkDto;
import es.docklite.docklitebackend.docker.entity.ResourceType;
import es.docklite.docklitebackend.user.entity.Role;
import es.docklite.docklitebackend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class NetworkService {

    private static final List<String> DEFAULT_NETWORKS = List.of("bridge", "host", "none");

    private final DockerClient dockerClient;
    private final ResourceOwnershipService ownershipService;
    private final ActivityLogService activityLogService;

    public List<NetworkDto> list(User currentUser) {
        List<Network> all = dockerClient.listNetworksCmd().exec();
        ContainerUsageIndex usage = ContainerUsageIndex.forUser(dockerClient, ownershipService, currentUser);

        if (currentUser.getRole() == Role.ADMIN) {
            return all.stream()
                    .map(n -> NetworkDto.from(n, usage.containersOnNetwork(n.getName())))
                    .toList();
        }

        List<String> ownedIds = ownershipService.getResourceIds(currentUser.getId(), ResourceType.NETWORK);
        return all.stream()
                .filter(n -> DEFAULT_NETWORKS.contains(n.getName()) || ownedIds.contains(n.getId()))
                .map(n -> NetworkDto.from(n, usage.containersOnNetwork(n.getName())))
                .toList();
    }

    /**
     * Same visibility rules as {@link #list(User)} but skips the
     * ContainerUsageIndex (an extra daemon round-trip) and the DTO mapping —
     * for callers that only need the number, like the dashboard summary.
     */
    public long countVisible(User currentUser) {
        List<Network> all = dockerClient.listNetworksCmd().exec();
        if (currentUser.getRole() == Role.ADMIN) {
            return all.size();
        }
        List<String> ownedIds = ownershipService.getResourceIds(currentUser.getId(), ResourceType.NETWORK);
        return all.stream()
                .filter(n -> DEFAULT_NETWORKS.contains(n.getName()) || ownedIds.contains(n.getId()))
                .count();
    }

    public NetworkDto create(CreateNetworkRequest req, User currentUser) {
        CreateNetworkResponse response = dockerClient.createNetworkCmd()
                .withName(req.name())
                .withDriver(req.driverOrDefault())
                .exec();

        ownershipService.register(response.getId(), ResourceType.NETWORK, req.name(), currentUser.getId());
        activityLogService.log(currentUser.getId(), response.getId(), ResourceType.NETWORK, ActivityAction.CREATE);

        return inspect(response.getId(), currentUser);
    }

    public NetworkDto inspect(String id, User currentUser) {
        var info = dockerClient.inspectNetworkCmd().withNetworkId(id).exec();
        if (!isDefaultNetwork(info.getName())) {
            checkAccess(info.getId(), currentUser);
        }
        // Rebuild the connected container list filtered by the user's
        // ownership — the inspect response would otherwise leak names of
        // other users' containers attached to default networks like bridge.
        ContainerUsageIndex usage = ContainerUsageIndex.forUser(dockerClient, ownershipService, currentUser);
        return NetworkDto.from(info, usage.containersOnNetwork(info.getName()));
    }

    public void remove(String id, User currentUser) {
        var info = dockerClient.inspectNetworkCmd().withNetworkId(id).exec();
        if (isDefaultNetwork(info.getName())) {
            throw new IllegalArgumentException("Cannot delete default network: " + info.getName());
        }
        checkAccess(info.getId(), currentUser);
        dockerClient.removeNetworkCmd(info.getId()).exec();
        ownershipService.unregister(info.getId(), ResourceType.NETWORK);
        activityLogService.log(currentUser.getId(), info.getId(), ResourceType.NETWORK, ActivityAction.DELETE);
    }

    public void connectContainer(String networkId, String containerId, User currentUser) {
        var info = dockerClient.inspectNetworkCmd().withNetworkId(networkId).exec();
        if (!isDefaultNetwork(info.getName())) {
            checkAccess(info.getId(), currentUser);
        }
        String fullContainerId = dockerClient.inspectContainerCmd(containerId).exec().getId();
        if (!ownershipService.hasAccess(fullContainerId, ResourceType.CONTAINER, currentUser)) {
            throw new AccessDeniedException(SecurityMessages.ACCESS_DENIED);
        }
        dockerClient.connectToNetworkCmd()
                .withNetworkId(info.getId())
                .withContainerId(fullContainerId)
                .exec();
        activityLogService.log(currentUser.getId(), info.getId(), ResourceType.NETWORK, ActivityAction.CONNECT);
    }

    public void disconnectContainer(String networkId, String containerId, User currentUser) {
        var info = dockerClient.inspectNetworkCmd().withNetworkId(networkId).exec();
        if (!isDefaultNetwork(info.getName())) {
            checkAccess(info.getId(), currentUser);
        }
        String fullContainerId = dockerClient.inspectContainerCmd(containerId).exec().getId();
        if (!ownershipService.hasAccess(fullContainerId, ResourceType.CONTAINER, currentUser)) {
            throw new AccessDeniedException(SecurityMessages.ACCESS_DENIED);
        }
        dockerClient.disconnectFromNetworkCmd()
                .withNetworkId(info.getId())
                .withContainerId(fullContainerId)
                .exec();
        activityLogService.log(currentUser.getId(), info.getId(), ResourceType.NETWORK, ActivityAction.DISCONNECT);
    }

    private boolean isDefaultNetwork(String name) {
        return DEFAULT_NETWORKS.contains(name);
    }

    private void checkAccess(String networkId, User user) {
        if (!ownershipService.hasAccess(networkId, ResourceType.NETWORK, user)) {
            throw new AccessDeniedException(SecurityMessages.ACCESS_DENIED);
        }
    }
}
