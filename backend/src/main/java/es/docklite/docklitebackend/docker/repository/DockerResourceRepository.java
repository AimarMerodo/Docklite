package es.docklite.docklitebackend.docker.repository;

import es.docklite.docklitebackend.docker.entity.DockerResource;
import es.docklite.docklitebackend.docker.entity.ResourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DockerResourceRepository extends JpaRepository<DockerResource, Long> {

    List<DockerResource> findByOwnerIdAndResourceType(Long ownerId, ResourceType type);

    boolean existsByResourceIdAndResourceTypeAndOwnerId(String resourceId, ResourceType type, Long ownerId);

    void deleteByResourceIdAndResourceType(String resourceId, ResourceType type);

    void deleteByResourceIdAndResourceTypeAndOwnerId(String resourceId, ResourceType type, Long ownerId);

    List<DockerResource> findByResourceIdAndResourceType(String resourceId, ResourceType type);

    List<DockerResource> findByResourceIdInAndResourceType(List<String> resourceIds, ResourceType type);
}
