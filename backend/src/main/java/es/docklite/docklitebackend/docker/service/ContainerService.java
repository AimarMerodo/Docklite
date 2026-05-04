package es.docklite.docklitebackend.docker.service;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.PullImageResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import es.docklite.docklitebackend.audit.entity.ActivityAction;
import es.docklite.docklitebackend.audit.service.ActivityLogService;
import es.docklite.docklitebackend.common.exception.DockerOperationException;
import es.docklite.docklitebackend.common.exception.SecurityMessages;
import es.docklite.docklitebackend.docker.dto.ContainerDto;
import es.docklite.docklitebackend.docker.dto.CreateContainerRequest;
import es.docklite.docklitebackend.docker.entity.ResourceType;
import es.docklite.docklitebackend.user.entity.Role;
import es.docklite.docklitebackend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ContainerService {

    private final DockerClient dockerClient;
    private final ResourceOwnershipService ownershipService;
    private final ActivityLogService activityLogService;

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

    public ContainerDto create(CreateContainerRequest req, User currentUser) {
        ensureImageAvailable(req.image(), currentUser);

        var cmd = dockerClient.createContainerCmd(req.image());
        if (req.name() != null && !req.name().isBlank()) {
            cmd = cmd.withName(req.name());
        }
        CreateContainerResponse response = cmd.exec();
        String containerId = response.getId();

        ownershipService.register(containerId, ResourceType.CONTAINER, req.name(), currentUser.getId());
        activityLogService.log(currentUser.getId(), containerId, ResourceType.CONTAINER, ActivityAction.CREATE);

        if (req.autoStart()) {
            dockerClient.startContainerCmd(containerId).exec();
            activityLogService.log(currentUser.getId(), containerId, ResourceType.CONTAINER, ActivityAction.START);
        }

        return inspect(containerId, currentUser);
    }

    public ContainerDto inspect(String id, User currentUser) {
        var info = dockerClient.inspectContainerCmd(id).exec();
        checkAccess(info.getId(), currentUser);
        return ContainerDto.from(info);
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

    public void remove(String id, User user) {
        String fullId = resolveFullId(id);
        checkAccess(fullId, user);
        dockerClient.removeContainerCmd(fullId).withForce(true).exec();
        ownershipService.unregister(fullId, ResourceType.CONTAINER);
        activityLogService.log(user.getId(), fullId, ResourceType.CONTAINER, ActivityAction.DELETE);
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

    /** Si la imagen no está en local, la descarga (pull-on-create). */
    private void ensureImageAvailable(String imageRef, User currentUser) {
        try {
            dockerClient.inspectImageCmd(imageRef).exec();
        } catch (NotFoundException notLocal) {
            String repo = imageRef;
            String tag = "latest";
            int colon = imageRef.lastIndexOf(':');
            if (colon > 0 && imageRef.indexOf('/', colon) == -1) {
                repo = imageRef.substring(0, colon);
                tag = imageRef.substring(colon + 1);
            }
            try {
                dockerClient.pullImageCmd(repo)
                        .withTag(tag)
                        .exec(new PullImageResultCallback())
                        .awaitCompletion();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new DockerOperationException("Pull interrupted", e);
            }
            String pulledId = dockerClient.inspectImageCmd(repo + ":" + tag).exec().getId();
            try {
                ownershipService.register(pulledId, ResourceType.IMAGE, repo + ":" + tag, currentUser.getId());
            } catch (org.springframework.dao.DataIntegrityViolationException ignored) {
                // ya estaba registrada para este user
            }
            activityLogService.log(currentUser.getId(), pulledId, ResourceType.IMAGE, ActivityAction.PULL);
        }
    }

    private void checkAccess(String containerId, User user) {
        if (!ownershipService.hasAccess(containerId, ResourceType.CONTAINER, user)) {
            throw new AccessDeniedException(SecurityMessages.ACCESS_DENIED);
        }
    }
}
