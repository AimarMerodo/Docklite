package es.docklite.docklitebackend.docker.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import es.docklite.docklitebackend.docker.dto.RemoteImageInfoDto;
import es.docklite.docklitebackend.docker.dto.SearchResultDto;
import es.docklite.docklitebackend.docker.entity.ResourceType;
import es.docklite.docklitebackend.user.entity.Role;
import es.docklite.docklitebackend.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ImageService {

    private final DockerClient dockerClient;
    private final ResourceOwnershipService ownershipService;
    private final ActivityLogService activityLogService;

    public List<ImageDto> list(User currentUser) {
        List<Image> all = dockerClient.listImagesCmd().exec();
        ContainerUsageIndex usage = ContainerUsageIndex.forUser(dockerClient, ownershipService, currentUser);

        if (currentUser.getRole() == Role.ADMIN) {
            // Admin sees everyone who pulled each image.
            List<String> ids = all.stream().map(Image::getId).toList();
            Map<String, List<String>> ownersByImage =
                    ownershipService.getOwnerNames(ids, ResourceType.IMAGE);
            return all.stream()
                    .map(img -> ImageDto.from(img,
                            ownersByImage.getOrDefault(img.getId(), List.of()),
                            usage.containersUsingImage(img.getId())))
                    .toList();
        }

        // Regular users only see themselves as owner — never other users'
        // names — even if multiple users have pulled the same image.
        List<String> ownedIds = ownershipService.getResourceIds(currentUser.getId(), ResourceType.IMAGE);
        List<String> selfOnly = List.of(currentUser.getUsername());
        return all.stream()
                .filter(img -> ownedIds.contains(img.getId()))
                .map(img -> ImageDto.from(img, selfOnly,
                        usage.containersUsingImage(img.getId())))
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

        // Same visibility rules as the inspect endpoint.
        List<String> owners;
        if (currentUser.getRole() == Role.ADMIN) {
            owners = ownershipService
                    .getOwnerNames(List.of(info.getId()), ResourceType.IMAGE)
                    .getOrDefault(info.getId(), List.of());
        } else {
            owners = List.of(currentUser.getUsername());
        }
        List<String> usedBy = ContainerUsageIndex
                .forUser(dockerClient, ownershipService, currentUser)
                .containersUsingImage(info.getId());
        return ImageDto.from(info, owners, usedBy);
    }

    public ImageDto inspect(String id, User currentUser) {
        InspectImageResponse info = dockerClient.inspectImageCmd(id).exec();
        List<String> owners;
        if (currentUser.getRole() == Role.ADMIN) {
            owners = ownershipService
                    .getOwnerNames(List.of(info.getId()), ResourceType.IMAGE)
                    .getOrDefault(info.getId(), List.of());
        } else {
            owners = List.of(currentUser.getUsername());
        }
        List<String> usedBy = ContainerUsageIndex
                .forUser(dockerClient, ownershipService, currentUser)
                .containersUsingImage(info.getId());
        return ImageDto.from(info, owners, usedBy);
    }

    public void remove(String id, User currentUser) {
        String fullId = dockerClient.inspectImageCmd(id).exec().getId();
        dockerClient.removeImageCmd(fullId).withForce(true).exec();
        ownershipService.unregister(fullId, ResourceType.IMAGE);
        activityLogService.log(currentUser.getId(), fullId, ResourceType.IMAGE, ActivityAction.DELETE);
    }

    private static final Pattern IMAGE_REF_PATTERN =
            Pattern.compile("^[a-zA-Z0-9._/-]+(:[a-zA-Z0-9._-]+)?(@sha256:[a-f0-9]{64})?$");
    private static final long REMOTE_INSPECT_TIMEOUT_SECONDS = 15;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Reads an image's {@code Config.ExposedPorts} from the registry without
     * downloading its layers. Backed by {@code docker buildx imagetools
     * inspect} (the docker CLI is bundled in the runtime container).
     * Only public images work without further auth setup.
     */
    public RemoteImageInfoDto inspectRemote(String ref) {
        if (ref == null || !IMAGE_REF_PATTERN.matcher(ref).matches()) {
            throw new IllegalArgumentException("Invalid image reference");
        }
        ProcessBuilder pb = new ProcessBuilder(
                "docker", "buildx", "imagetools", "inspect", ref, "--format", "{{json .Image}}");
        pb.redirectErrorStream(true);
        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            throw new DockerOperationException("Could not invoke docker CLI: " + e.getMessage(), e);
        }

        // The CLI can produce 80+ KB of JSON; if we don't drain the pipe
        // concurrently the subprocess blocks on write while waitFor() blocks
        // on it, deadlocking until the timeout fires. Drain in a virtual
        // thread so the subprocess can finish naturally.
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        Thread drain = Thread.ofVirtual().start(() -> {
            try {
                p.getInputStream().transferTo(buf);
            } catch (IOException ignored) {
                // pipe closed / process killed — buffer keeps what we got
            }
        });

        boolean completed;
        try {
            completed = p.waitFor(REMOTE_INSPECT_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            p.destroyForcibly();
            throw new DockerOperationException("Remote image inspect interrupted", e);
        }
        if (!completed) {
            p.destroyForcibly();
            drain.interrupt();
            throw new DockerOperationException("Remote image inspect timed out for " + ref);
        }
        try {
            drain.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        String stdout = buf.toString();

        if (p.exitValue() != 0) {
            throw new IllegalArgumentException(
                    "Image not available in registry: " + ref +
                    (stdout.isBlank() ? "" : " — " + stdout.trim()));
        }

        return new RemoteImageInfoDto(ref, parseExposedPorts(stdout));
    }

    /**
     * Extracts {@code ExposedPorts} from {@code docker buildx imagetools
     * inspect --format '{{json .Image}}'} output. The shape is either:
     *   - a single image config object (single-arch image): {@code {Config: {ExposedPorts: {...}}}}
     *   - a map keyed by platform (multi-arch): {@code {"linux/amd64": {Config: {...}}, ...}}
     */
    private static List<ImageDto.ExposedPortInfo> parseExposedPorts(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode root = MAPPER.readTree(json);
            JsonNode image = pickPlatformNode(root);
            if (image == null || image.isMissingNode() || image.isNull()) return List.of();
            // Image config can be at "Config" (single-arch) or "config" (lowercase, OCI flavor)
            JsonNode config = image.has("Config") ? image.get("Config") : image.get("config");
            if (config == null || !config.isObject()) return List.of();
            JsonNode exposed = config.has("ExposedPorts") ? config.get("ExposedPorts") : config.get("exposedPorts");
            if (exposed == null || !exposed.isObject()) return List.of();

            List<ImageDto.ExposedPortInfo> out = new ArrayList<>();
            Iterator<Map.Entry<String, JsonNode>> it = exposed.fields();
            while (it.hasNext()) {
                String key = it.next().getKey(); // e.g. "80/tcp" or "5432/tcp"
                int slash = key.indexOf('/');
                if (slash <= 0) continue;
                int port;
                try {
                    port = Integer.parseInt(key.substring(0, slash));
                } catch (NumberFormatException nfe) {
                    continue;
                }
                String proto = key.substring(slash + 1).toLowerCase();
                out.add(new ImageDto.ExposedPortInfo(port, proto));
            }
            return out;
        } catch (IOException e) {
            return List.of();
        }
    }

    private static JsonNode pickPlatformNode(JsonNode root) {
        if (root == null) return null;
        // Single-arch: object directly with Config
        if (root.has("Config") || root.has("config")) return root;
        // Multi-arch: pick linux/amd64, fall back to first linux/* entry, then any
        if (root.isObject()) {
            if (root.has("linux/amd64")) return root.get("linux/amd64");
            Iterator<Map.Entry<String, JsonNode>> it = root.fields();
            JsonNode firstLinux = null;
            JsonNode firstAny = null;
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> e = it.next();
                if (firstAny == null) firstAny = e.getValue();
                if (e.getKey().startsWith("linux/") && firstLinux == null) firstLinux = e.getValue();
            }
            return firstLinux != null ? firstLinux : firstAny;
        }
        return null;
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
