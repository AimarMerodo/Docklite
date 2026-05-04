package es.docklite.docklitebackend.docker.service;

import com.github.dockerjava.api.command.CreateVolumeResponse;
import com.github.dockerjava.api.command.InspectVolumeResponse;
import com.github.dockerjava.api.DockerClient;
import es.docklite.docklitebackend.audit.entity.ActivityAction;
import es.docklite.docklitebackend.audit.service.ActivityLogService;
import es.docklite.docklitebackend.common.exception.SecurityMessages;
import es.docklite.docklitebackend.docker.dto.CreateVolumeRequest;
import es.docklite.docklitebackend.docker.dto.VolumeDto;
import es.docklite.docklitebackend.docker.entity.ResourceType;
import es.docklite.docklitebackend.user.entity.Role;
import es.docklite.docklitebackend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class VolumeService {

    private final DockerClient dockerClient;
    private final ResourceOwnershipService ownershipService;
    private final ActivityLogService activityLogService;

    public List<VolumeDto> list(User currentUser) {
        List<InspectVolumeResponse> all = dockerClient.listVolumesCmd().exec().getVolumes();

        if (currentUser.getRole() == Role.ADMIN) {
            return all.stream().map(VolumeDto::from).toList();
        }

        List<String> ownedNames = ownershipService.getResourceIds(currentUser.getId(), ResourceType.VOLUME);
        return all.stream()
                .filter(v -> ownedNames.contains(v.getName()))
                .map(VolumeDto::from)
                .toList();
    }

    public VolumeDto create(CreateVolumeRequest req, User currentUser) {
        CreateVolumeResponse response = dockerClient.createVolumeCmd()
                .withName(req.name())
                .withDriver(req.driverOrDefault())
                .exec();

        ownershipService.register(response.getName(), ResourceType.VOLUME, response.getName(), currentUser.getId());
        activityLogService.log(currentUser.getId(), response.getName(), ResourceType.VOLUME, ActivityAction.CREATE);

        return inspect(response.getName(), currentUser);
    }

    public VolumeDto inspect(String name, User currentUser) {
        checkAccess(name, currentUser);
        return VolumeDto.from(dockerClient.inspectVolumeCmd(name).exec());
    }

    public void remove(String name, User currentUser) {
        checkAccess(name, currentUser);
        dockerClient.removeVolumeCmd(name).exec();
        ownershipService.unregister(name, ResourceType.VOLUME);
        activityLogService.log(currentUser.getId(), name, ResourceType.VOLUME, ActivityAction.DELETE);
    }

    private void checkAccess(String volumeName, User user) {
        if (!ownershipService.hasAccess(volumeName, ResourceType.VOLUME, user)) {
            throw new AccessDeniedException(SecurityMessages.ACCESS_DENIED);
        }
    }
}
