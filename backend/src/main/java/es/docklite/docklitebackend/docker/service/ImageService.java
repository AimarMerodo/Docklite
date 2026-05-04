package es.docklite.docklitebackend.docker.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.InspectImageResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.model.Image;
import com.github.dockerjava.api.model.SearchItem;
import es.docklite.docklitebackend.audit.entity.ActivityAction;
import es.docklite.docklitebackend.audit.service.ActivityLogService;
import es.docklite.docklitebackend.common.exception.DockerOperationException;
import es.docklite.docklitebackend.docker.dto.ImageDto;
import es.docklite.docklitebackend.docker.dto.PullImageRequest;
import es.docklite.docklitebackend.docker.dto.SearchResultDto;
import es.docklite.docklitebackend.docker.entity.ResourceType;
import es.docklite.docklitebackend.user.entity.Role;
import es.docklite.docklitebackend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ImageService {

    private final DockerClient dockerClient;
    private final ResourceOwnershipService ownershipService;
    private final ActivityLogService activityLogService;

    public List<ImageDto> list(User currentUser) {
        List<Image> all = dockerClient.listImagesCmd().exec();

        if (currentUser.getRole() == Role.ADMIN) {
            return all.stream().map(ImageDto::from).toList();
        }

        List<String> ownedIds = ownershipService.getResourceIds(currentUser.getId(), ResourceType.IMAGE);
        return all.stream()
                .filter(img -> ownedIds.contains(img.getId()))
                .map(ImageDto::from)
                .toList();
    }

    public ImageDto pull(PullImageRequest req, User currentUser) {
        try {
            dockerClient.pullImageCmd(req.image())
                    .withTag(req.tagOrLatest())
                    .exec(new PullImageResultCallback())
                    .awaitCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DockerOperationException("Pull interrupted", e);
        }

        InspectImageResponse info = dockerClient.inspectImageCmd(req.fullReference()).exec();
        registerOwnershipIfNew(info.getId(), req.fullReference(), currentUser.getId());
        activityLogService.log(currentUser.getId(), info.getId(), ResourceType.IMAGE, ActivityAction.PULL);
        return ImageDto.from(info);
    }

    public ImageDto inspect(String id) {
        return ImageDto.from(dockerClient.inspectImageCmd(id).exec());
    }

    public void remove(String id, User currentUser) {
        String fullId = dockerClient.inspectImageCmd(id).exec().getId();
        dockerClient.removeImageCmd(fullId).withForce(true).exec();
        ownershipService.unregister(fullId, ResourceType.IMAGE);
        activityLogService.log(currentUser.getId(), fullId, ResourceType.IMAGE, ActivityAction.DELETE);
    }

    public List<SearchResultDto> search(String query) {
        List<SearchItem> results = dockerClient.searchImagesCmd(query).exec();
        return results.stream().map(SearchResultDto::from).toList();
    }

    /** Registra ownership si el user aún no lo tenía. La constraint UNIQUE evita duplicados. */
    void registerOwnershipIfNew(String imageId, String imageName, Long ownerId) {
        try {
            ownershipService.register(imageId, ResourceType.IMAGE, imageName, ownerId);
        } catch (DataIntegrityViolationException ignored) {
            // ya estaba registrado para este user
        }
    }
}
