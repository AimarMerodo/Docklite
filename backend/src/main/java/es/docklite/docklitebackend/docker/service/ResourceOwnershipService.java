package es.docklite.docklitebackend.docker.service;

import es.docklite.docklitebackend.docker.entity.DockerResource;
import es.docklite.docklitebackend.docker.entity.ResourceType;
import es.docklite.docklitebackend.docker.repository.DockerResourceRepository;
import es.docklite.docklitebackend.user.entity.Role;
import es.docklite.docklitebackend.user.entity.User;
import es.docklite.docklitebackend.user.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ResourceOwnershipService {

    private final DockerResourceRepository resourceRepository;
    private final UserRepository userRepository;

    public void register(String resourceId, ResourceType type, String name, Long ownerId) {
        DockerResource res = DockerResource.builder()
                .resourceId(resourceId)
                .resourceType(type)
                .resourceName(name)
                .ownerId(ownerId)
                .build();
        resourceRepository.save(res);
    }

    public List<String> getResourceIds(Long userId, ResourceType type) {
        return resourceRepository.findByOwnerIdAndResourceType(userId, type)
                .stream()
                .map(DockerResource::getResourceId)
                .toList();
    }

    public boolean hasAccess(String resourceId, ResourceType type, User user) {
        if (user.getRole() == Role.ADMIN) return true;
        return resourceRepository.existsByResourceIdAndResourceTypeAndOwnerId(
                resourceId, type, user.getId()
        );
    }

    @Transactional
    public void unregister(String resourceId, ResourceType type) {
        resourceRepository.deleteByResourceIdAndResourceType(resourceId, type);
    }

    @Transactional
    public void unregisterForUser(String resourceId, ResourceType type, Long ownerId) {
        resourceRepository.deleteByResourceIdAndResourceTypeAndOwnerId(resourceId, type, ownerId);
    }

    /** Update the human-readable name on every ownership row of the given resource. */
    @Transactional
    public void renameResource(String resourceId, ResourceType type, String newName) {
        for (DockerResource r : resourceRepository.findByResourceIdAndResourceType(resourceId, type)) {
            r.setResourceName(newName);
            resourceRepository.save(r);
        }
    }

    /**
     * Returns a map of {@code resourceId → ordered list of owner usernames}
     * for the given resource ids and type. Resources with no ownership row
     * are absent from the map (empty list semantics — callers should default).
     */
    public Map<String, List<String>> getOwnerNames(List<String> resourceIds, ResourceType type) {
        if (resourceIds == null || resourceIds.isEmpty()) return Map.of();
        List<DockerResource> rows = resourceRepository
                .findByResourceIdInAndResourceType(resourceIds, type);
        if (rows.isEmpty()) return Map.of();

        Set<Long> ownerIds = new HashSet<>();
        for (DockerResource r : rows) ownerIds.add(r.getOwnerId());

        Map<Long, String> usernameByOwnerId = userRepository.findAllById(ownerIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        Map<String, List<String>> out = new HashMap<>();
        for (DockerResource r : rows) {
            String username = usernameByOwnerId.get(r.getOwnerId());
            if (username == null) continue;
            out.computeIfAbsent(r.getResourceId(), k -> new java.util.ArrayList<>()).add(username);
        }
        // Make each list deterministic and de-duplicated.
        out.replaceAll((k, v) -> v.stream().distinct().sorted().toList());
        return out;
    }
}
