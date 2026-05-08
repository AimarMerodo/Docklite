package es.docklite.docklitebackend.docker.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.ContainerMount;
import com.github.dockerjava.api.model.ContainerNetwork;
import com.github.dockerjava.api.model.ContainerNetworkSettings;
import es.docklite.docklitebackend.docker.entity.ResourceType;
import es.docklite.docklitebackend.user.entity.Role;
import es.docklite.docklitebackend.user.entity.User;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Snapshot of which containers currently reference each image / volume /
 * network on the host. Built from a single {@code listContainersCmd}
 * call so the listing endpoints can answer "who's using me" without N+1
 * inspect queries.
 */
public final class ContainerUsageIndex {

    /** image id (sha) → container names that reference that image. */
    private final Map<String, List<String>> byImageId;
    /** volume name → container names that mount that volume. */
    private final Map<String, List<String>> byVolumeName;
    /** network name → container names that are connected. */
    private final Map<String, List<String>> byNetworkName;

    private ContainerUsageIndex(
            Map<String, List<String>> byImageId,
            Map<String, List<String>> byVolumeName,
            Map<String, List<String>> byNetworkName) {
        this.byImageId = byImageId;
        this.byVolumeName = byVolumeName;
        this.byNetworkName = byNetworkName;
    }

    public List<String> containersUsingImage(String imageId) {
        return byImageId.getOrDefault(imageId, List.of());
    }

    public List<String> containersUsingVolume(String volumeName) {
        return byVolumeName.getOrDefault(volumeName, List.of());
    }

    public List<String> containersOnNetwork(String networkName) {
        return byNetworkName.getOrDefault(networkName, List.of());
    }

    /**
     * Builds the index from the host's current container list. Includes
     * stopped containers — they still hold references to images/volumes
     * for our purposes (you can't delete an image used by a stopped
     * container either).
     */
    public static ContainerUsageIndex from(DockerClient client) {
        return buildFrom(client.listContainersCmd().withShowAll(true).exec(), null);
    }

    /**
     * Same as {@link #from(DockerClient)} but only includes containers the
     * given user has access to (admin sees everything). Use this when the
     * resulting index is exposed to a non-admin user — otherwise we'd leak
     * other users' container names through {@code usedBy} / {@code
     * connectedContainers} fields.
     */
    public static ContainerUsageIndex forUser(DockerClient client,
                                              ResourceOwnershipService ownership,
                                              User user) {
        List<Container> all = client.listContainersCmd().withShowAll(true).exec();
        if (user.getRole() == Role.ADMIN) {
            return buildFrom(all, null);
        }
        Set<String> visibleIds = new HashSet<>(
                ownership.getResourceIds(user.getId(), ResourceType.CONTAINER));
        return buildFrom(all, visibleIds);
    }

    private static ContainerUsageIndex buildFrom(List<Container> containers, Set<String> visibleIds) {

        Map<String, List<String>> byImageId = new HashMap<>();
        Map<String, List<String>> byVolumeName = new HashMap<>();
        Map<String, List<String>> byNetworkName = new HashMap<>();

        for (Container c : containers) {
            // For non-admin users, skip containers they don't own — otherwise
            // we'd leak the names of other users' containers via usedBy /
            // connectedContainers.
            if (visibleIds != null && !visibleIds.contains(c.getId())) continue;

            String name = friendlyName(c);

            if (c.getImageId() != null) {
                byImageId.computeIfAbsent(c.getImageId(), k -> new ArrayList<>()).add(name);
            }

            if (c.getMounts() != null) {
                for (ContainerMount m : c.getMounts()) {
                    // ContainerMount.getName() is non-null only for named
                    // volumes; bind mounts have no global identity.
                    if (m.getName() != null && !m.getName().isBlank()) {
                        byVolumeName.computeIfAbsent(m.getName(), k -> new ArrayList<>()).add(name);
                    }
                }
            }

            ContainerNetworkSettings ns = c.getNetworkSettings();
            if (ns != null && ns.getNetworks() != null) {
                for (Map.Entry<String, ContainerNetwork> e : ns.getNetworks().entrySet()) {
                    byNetworkName.computeIfAbsent(e.getKey(), k -> new ArrayList<>()).add(name);
                }
            }
        }

        // Make each list deterministic and de-duplicated.
        sortDistinctInPlace(byImageId);
        sortDistinctInPlace(byVolumeName);
        sortDistinctInPlace(byNetworkName);

        return new ContainerUsageIndex(byImageId, byVolumeName, byNetworkName);
    }

    private static String friendlyName(Container c) {
        if (c.getNames() != null && c.getNames().length > 0) {
            return c.getNames()[0].replaceFirst("^/", "");
        }
        return c.getId() != null && c.getId().length() >= 12
                ? c.getId().substring(0, 12)
                : c.getId();
    }

    private static void sortDistinctInPlace(Map<String, List<String>> map) {
        map.replaceAll((k, v) -> v.stream().distinct().sorted().toList());
    }
}
