package es.docklite.docklitebackend.docker.service;

import es.docklite.docklitebackend.docker.entity.DockerResource;
import es.docklite.docklitebackend.docker.entity.ResourceType;
import es.docklite.docklitebackend.docker.repository.DockerResourceRepository;
import es.docklite.docklitebackend.user.entity.Role;
import es.docklite.docklitebackend.user.entity.User;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ResourceOwnershipService {

    private final DockerResourceRepository resourceRepository;

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
}
