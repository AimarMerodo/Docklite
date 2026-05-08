package es.docklite.docklitebackend.docker.dto;

import com.github.dockerjava.api.model.Network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public record NetworkDto(
        String id,
        String name,
        String driver,
        String scope,
        List<ConnectedContainer> connectedContainers
) {

    public record ConnectedContainer(String id, String name) {}

    public static NetworkDto from(Network n) {
        return new NetworkDto(
                n.getId(),
                n.getName(),
                n.getDriver(),
                n.getScope(),
                buildConnected(n)
        );
    }

    /**
     * Variant that takes an explicit list of connected container names —
     * used both by the list endpoint (where the daemon's network list does
     * not include containers) and by inspect to filter by ownership before
     * returning. The list is used as-is; pass an empty list for "no visible
     * containers", null only if you intentionally want the daemon-supplied
     * fallback (rare).
     */
    public static NetworkDto from(Network n, List<String> connectedContainerNames) {
        List<ConnectedContainer> connected;
        if (connectedContainerNames == null) {
            connected = buildConnected(n);
        } else if (connectedContainerNames.isEmpty()) {
            connected = List.of();
        } else {
            List<ConnectedContainer> out = new ArrayList<>(connectedContainerNames.size());
            for (String name : connectedContainerNames) {
                out.add(new ConnectedContainer("", name));
            }
            connected = out;
        }
        return new NetworkDto(
                n.getId(),
                n.getName(),
                n.getDriver(),
                n.getScope(),
                connected
        );
    }

    private static List<ConnectedContainer> buildConnected(Network n) {
        Map<String, Network.ContainerNetworkConfig> containers = n.getContainers();
        if (containers == null || containers.isEmpty()) {
            return List.of();
        }
        List<ConnectedContainer> out = new ArrayList<>(containers.size());
        for (Map.Entry<String, Network.ContainerNetworkConfig> e : containers.entrySet()) {
            String name = e.getValue() != null ? e.getValue().getName() : null;
            out.add(new ConnectedContainer(e.getKey(), name));
        }
        return out;
    }
}
