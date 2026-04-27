# Docklite — Hoja de Ruta Backend v4 (definitiva)

> **Perfil:** Junior, primer proyecto Spring Boot, aprendiendo Java en paralelo
> **Deadline:** 4 de mayo de 2026 (~5.5 semanas)
> **Fuente:** Memoria_Docklite.docx — Requisitos funcionales RF-01 a RF-09

---

## Resumen de requisitos (según la memoria)

| RF | Descripción | Prioridad |
|----|-------------|-----------|
| RF-01 | Registro de usuarios | CORE |
| RF-02 | Autenticación por credenciales | CORE |
| RF-03 | Roles: usuario y administrador | CORE |
| RF-04 | Usuario gestiona SOLO sus contenedores | CORE |
| RF-05 | Admin gestiona TODOS los recursos | CORE |
| RF-06 | Crear, listar, eliminar contenedores | CORE |
| RF-07 | Crear, listar, eliminar volúmenes | CORE |
| RF-08 | Crear, listar, eliminar redes | CORE |
| RF-09 | Interfaz web accesible remotamente | Frontend (Angular) |

---

## Política de permisos por recurso

| Recurso | Listar | Pull/Crear | Borrar | Ownership en DB |
|---|---|---|---|---|
| Contenedores | Solo míos (admin: todos) | Cualquier user | Solo mío (admin: cualquiera) | Sí |
| Imágenes | Solo las que yo pullé (admin: todas) | Cualquier user | Solo admin | Sí (para filtrar listado) |
| Redes | Mis redes + default (admin: todas) | Cualquier user | Solo mía (admin: cualquiera) | Sí |
| Volúmenes | Solo míos (admin: todos) | Cualquier user | Solo mío (admin: cualquiera) | Sí |

### Notas sobre imágenes
- Docker comparte imágenes a nivel de sistema: si dos users pullean `nginx:latest`, solo existe una copia en disco.
- El ownership en DB sirve para **filtrar el listado**, no para controlar el borrado.
- Cuando un user hace pull, se registra en `docker_resources` para que le aparezca en SU listado.
- Dos users pueden pullear la misma imagen → ambos la ven en su listado.
- Solo el admin puede borrar imágenes (para evitar que un user borre una imagen que usa otro).

### Notas sobre redes
- Las redes default de Docker (bridge, host, none) son visibles para todos.
- Las redes creadas por un user solo las ve él (y el admin).
- Al conectar un contenedor a una red, se verifica que el user tenga acceso a AMBOS recursos.

---

## Modelo de base de datos

```sql
-- V1__create_users.sql
CREATE TABLE users (
    id            BIGSERIAL    PRIMARY KEY,
    username      VARCHAR(50)  UNIQUE NOT NULL,
    email         VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL DEFAULT 'USER',  -- USER | ADMIN
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);

-- V2__create_docker_resources.sql
CREATE TABLE docker_resources (
    id            BIGSERIAL    PRIMARY KEY,
    resource_id   VARCHAR(64)  NOT NULL,        -- ID del recurso en Docker
    resource_type VARCHAR(20)  NOT NULL,         -- CONTAINER | IMAGE | VOLUME | NETWORK
    resource_name VARCHAR(255),                  -- nombre legible (nginx:latest, my-network, etc.)
    owner_id      BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW(),
    UNIQUE(resource_id, resource_type, owner_id) -- misma imagen puede tener múltiples owners
);

-- V3__create_activity_log.sql
CREATE TABLE activity_log (
    id            BIGSERIAL    PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES users(id),
    resource_id   VARCHAR(64),
    resource_type VARCHAR(20),
    action        VARCHAR(50)  NOT NULL,         -- CREATE, DELETE, START, STOP, PULL, etc.
    created_at    TIMESTAMP    NOT NULL DEFAULT NOW()
);
```

### Por qué `UNIQUE(resource_id, resource_type, owner_id)`
- Un contenedor tiene UN dueño → solo hay una fila por contenedor.
- Una imagen puede ser pulleada por VARIOS users → hay una fila por cada user que la pulleó.
- La constraint evita duplicados (que el mismo user registre la misma imagen dos veces).

---

## Arquitectura: el flujo de ownership

```
1. User hace petición al backend (ej: crear contenedor, pull imagen)
2. Backend ejecuta la operación en Docker via docker-java
3. Backend registra ownership en tabla docker_resources
4. Cuando el user lista recursos:
   - Si es USER → backend consulta docker_resources para obtener SUS IDs
                 → filtra la respuesta de Docker para devolver solo esos
   - Si es ADMIN → backend devuelve todo lo de Docker sin filtrar
5. Cuando el user opera sobre un recurso (start, stop, delete...):
   - Backend verifica ownership en docker_resources
   - Si no es dueño NI admin → 403 Forbidden
```

---

## Estructura del proyecto

```
src/main/java/com/docklite/
├── DockLiteApplication.java
│
├── config/
│   ├── SecurityConfig.java
│   ├── DockerConfig.java
│   └── CorsConfig.java
│
├── auth/
│   ├── controller/AuthController.java
│   ├── dto/LoginRequest.java
│   ├── dto/RegisterRequest.java
│   ├── dto/AuthResponse.java
│   ├── service/AuthService.java
│   └── jwt/
│       ├── JwtProvider.java
│       └── JwtAuthFilter.java
│
├── user/
│   ├── controller/UserController.java
│   ├── dto/UserDto.java
│   ├── dto/UpdateProfileRequest.java
│   ├── entity/User.java
│   ├── entity/Role.java
│   ├── repository/UserRepository.java
│   └── service/UserService.java
│
├── container/
│   ├── controller/ContainerController.java
│   ├── dto/ContainerDto.java
│   ├── dto/CreateContainerRequest.java
│   ├── service/ContainerService.java
│   └── mapper/ContainerMapper.java
│
├── image/
│   ├── controller/ImageController.java
│   ├── dto/ImageDto.java
│   ├── dto/PullImageRequest.java
│   ├── service/ImageService.java
│   └── mapper/ImageMapper.java
│
├── network/
│   ├── controller/NetworkController.java
│   ├── dto/NetworkDto.java
│   ├── dto/CreateNetworkRequest.java
│   ├── service/NetworkService.java
│   └── mapper/NetworkMapper.java
│
├── volume/
│   ├── controller/VolumeController.java
│   ├── dto/VolumeDto.java
│   ├── dto/CreateVolumeRequest.java
│   ├── service/VolumeService.java
│   └── mapper/VolumeMapper.java
│
├── resource/
│   ├── entity/DockerResource.java
│   ├── entity/ResourceType.java      -- enum: CONTAINER, IMAGE, VOLUME, NETWORK
│   ├── repository/DockerResourceRepository.java
│   └── service/ResourceOwnershipService.java
│
├── system/
│   ├── controller/SystemController.java
│   └── dto/DashboardSummaryDto.java
│
└── exception/
    ├── GlobalExceptionHandler.java
    ├── ErrorResponse.java
    ├── UnauthorizedException.java
    └── DockerOperationException.java
```

---

## FASE 0 — Aprender Spring Boot (4 días: 28-31 mar)

No escribas código de Docklite todavía. Haz un mini-proyecto de prueba.

### Día 1 — Conceptos base
- Qué es un Bean, qué hace @Autowired, qué es inyección de dependencias
- Qué hace @SpringBootApplication al arrancar
- Diferencia entre @Component, @Service, @Repository, @Controller
- Estructura de carpetas y application.yml
- **Ejercicio:** crear proyecto en start.spring.io, arrancar, que devuelva "Hola" en GET /

### Día 2 — REST Controllers
- @RestController, @GetMapping, @PostMapping, @PutMapping, @DeleteMapping
- @PathVariable, @RequestParam, @RequestBody
- ResponseEntity para controlar status codes
- **Ejercicio:** CRUD de "notas" con datos hardcodeados (sin DB), probando en Postman

### Día 3 — Spring Data JPA + PostgreSQL
- @Entity, @Table, @Id, @GeneratedValue
- Qué es JpaRepository y cómo genera queries automáticas (findByX, existsByX)
- Conectar a PostgreSQL: dependencia + yml
- **Ejercicio:** conectar el CRUD de notas a PostgreSQL real

### Día 4 — Spring Security conceptos
- La cadena de filtros de Security
- SecurityFilterChain y cómo configurar rutas públicas/protegidas
- Qué es JWT y por qué se usa (ya lo sabes de Node, aquí es igual)
- **Solo leer/entender**, no implementar todavía

### ✅ Resultado Fase 0:
Puedes crear un @RestController que haga CRUD contra PostgreSQL.
Entiendes el flujo Controller → Service → Repository.

---

## FASE 1 — Auth + Usuarios (Semana 1: 1-6 abr)

> Cubre: RF-01 (registro), RF-02 (autenticación), RF-03 (roles)

### Paso a paso:

**Día 1-2: Crear proyecto + PostgreSQL + Flyway + User entity**

start.spring.io:
- Maven, Java 21, Spring Boot 3.4.x
- Group: `com.docklite`, Artifact: `docklite-backend`
- Dependencias: Spring Web, Spring Security, Spring Data JPA,
  PostgreSQL Driver, Validation, Lombok, Flyway Migration

Añadir manualmente al pom.xml:
```xml
<!-- JWT -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.6</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.6</version>
    <scope>runtime</scope>
</dependency>

<!-- Swagger / OpenAPI -->
<dependency>
    <groupId>org.springdoc</groupId>
    <artifactId>springdoc-openapi-starter-webmvc-ui</artifactId>
    <version>2.7.0</version>
</dependency>
```

application.yml:
```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/docklite
    username: docklite
    password: ${DB_PASSWORD:docklite}
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: true
  flyway:
    enabled: true

jwt:
  secret: ${JWT_SECRET:una-clave-secreta-de-al-menos-256-bits-para-hmac-sha}
  expiration: 86400000

server:
  port: 8080
```

Entidades:
```java
public enum Role { USER, ADMIN }
```

```java
@Entity @Table(name = "users")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false, length = 50)
    private String username;

    @Column(unique = true, nullable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        createdAt = updatedAt = LocalDateTime.now();
        if (role == null) role = Role.USER;
    }

    @PreUpdate
    void onUpdate() { updatedAt = LocalDateTime.now(); }
}
```

Repository:
```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByUsername(String username);
    boolean existsByEmail(String email);
    boolean existsByUsername(String username);
}
```

**Día 3: JWT — JwtProvider + JwtAuthFilter**

```java
@Component
public class JwtProvider {
    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long expiration;

    public String generateToken(User user) {
        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("username", user.getUsername())
            .claim("role", user.getRole().name())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + expiration))
            .signWith(getSigningKey())
            .compact();
    }

    public Long getUserIdFromToken(String token) {
        return Long.parseLong(getClaims(token).getSubject());
    }

    public boolean validateToken(String token) {
        try {
            getClaims(token);
            return true;
        } catch (JwtException e) {
            return false;
        }
    }

    private Claims getClaims(String token) {
        return Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }
}
```

```java
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                     HttpServletResponse response,
                                     FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String token = header.substring(7);
            if (jwtProvider.validateToken(token)) {
                Long userId = jwtProvider.getUserIdFromToken(token);
                userRepository.findById(userId).ifPresent(user -> {
                    var authToken = new UsernamePasswordAuthenticationToken(
                        user, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))
                    );
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                });
            }
        }
        chain.doFilter(request, response);
    }
}
```

**Día 4: SecurityConfig + AuthService + AuthController**

```java
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {
    private final JwtAuthFilter jwtAuthFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        return http
            .csrf(csrf -> csrf.disable())
            .cors(Customizer.withDefaults())
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/**").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .anyRequest().authenticated()
            )
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
```

DTOs:
```java
public record RegisterRequest(
    @NotBlank String username,
    @NotBlank @Email String email,
    @NotBlank @Size(min = 6) String password
) {}

public record LoginRequest(
    @NotBlank String email,
    @NotBlank String password
) {}

public record AuthResponse(String token, String username, String role) {}
```

```java
@Service
@RequiredArgsConstructor
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtProvider jwtProvider;

    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.email()))
            throw new IllegalArgumentException("Email ya registrado");
        if (userRepository.existsByUsername(req.username()))
            throw new IllegalArgumentException("Username ya existe");

        User user = User.builder()
            .username(req.username())
            .email(req.email())
            .passwordHash(passwordEncoder.encode(req.password()))
            .role(Role.USER)
            .build();

        userRepository.save(user);
        String token = jwtProvider.generateToken(user);
        return new AuthResponse(token, user.getUsername(), user.getRole().name());
    }

    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByEmail(req.email())
            .orElseThrow(() -> new IllegalArgumentException("Credenciales inválidas"));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash()))
            throw new IllegalArgumentException("Credenciales inválidas");

        String token = jwtProvider.generateToken(user);
        return new AuthResponse(token, user.getUsername(), user.getRole().name());
    }
}
```

```java
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(req));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }
}
```

**Día 5: UserController**

```java
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public UserDto getProfile(Authentication auth) {
        User user = (User) auth.getPrincipal();
        return UserDto.from(user);
    }

    @PutMapping("/me")
    public UserDto updateProfile(Authentication auth,
                                  @Valid @RequestBody UpdateProfileRequest req) {
        User user = (User) auth.getPrincipal();
        return userService.updateProfile(user.getId(), req);
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserDto> listUsers() {
        return userService.findAll();
    }
}
```

```java
public record UserDto(Long id, String username, String email, String role, LocalDateTime createdAt) {
    public static UserDto from(User u) {
        return new UserDto(u.getId(), u.getUsername(), u.getEmail(), u.getRole().name(), u.getCreatedAt());
    }
}
```

**Día 6: Probar todo en Postman/Swagger**

### ✅ Checkpoint Fase 1:
- [ ] POST /api/auth/register → crea usuario, devuelve JWT
- [ ] POST /api/auth/login → valida credenciales, devuelve JWT
- [ ] GET /api/users/me → devuelve perfil con token válido
- [ ] GET /api/users → solo funciona con rol ADMIN
- [ ] Sin token → 401 en rutas protegidas

---

## FASE 2 — Docker: Contenedores (Semana 2: 7-13 abr)

> Cubre: RF-06, RF-04, RF-05

### Qué estudiar primero (medio día):
- Docker Engine API: REST sobre un socket Unix
- docker-java: wrapper Java, comandos tipo builder
- Revisar GitHub de docker-java para ver los comandos disponibles

### Dependencias Maven:
```xml
<dependency>
    <groupId>com.github.docker-java</groupId>
    <artifactId>docker-java-core</artifactId>
    <version>3.4.1</version>
</dependency>
<dependency>
    <groupId>com.github.docker-java</groupId>
    <artifactId>docker-java-transport-httpclient5</artifactId>
    <version>3.4.1</version>
</dependency>
```

### Día 1: DockerConfig + DockerResource entity + ResourceOwnershipService

```java
@Configuration
public class DockerConfig {
    @Bean
    public DockerClient dockerClient() {
        DockerClientConfig config = DefaultDockerClientConfig
            .createDefaultConfigBuilder()
            .withDockerHost("unix:///var/run/docker.sock")
            .build();
        DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
            .dockerHost(config.getDockerHost())
            .build();
        return DockerClientImpl.getInstance(config, httpClient);
    }
}
```

```java
public enum ResourceType { CONTAINER, IMAGE, VOLUME, NETWORK }
```

```java
@Entity @Table(name = "docker_resources",
    uniqueConstraints = @UniqueConstraint(columns = {"resource_id", "resource_type", "owner_id"}))
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class DockerResource {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resource_id", nullable = false, length = 64)
    private String resourceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 20)
    private ResourceType resourceType;

    @Column(name = "resource_name")
    private String resourceName;

    @Column(name = "owner_id", nullable = false)
    private Long ownerId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() { createdAt = LocalDateTime.now(); }
}
```

```java
public interface DockerResourceRepository extends JpaRepository<DockerResource, Long> {
    List<DockerResource> findByOwnerIdAndResourceType(Long ownerId, ResourceType type);

    boolean existsByResourceIdAndResourceTypeAndOwnerId(
        String resourceId, ResourceType type, Long ownerId);

    void deleteByResourceIdAndResourceType(String resourceId, ResourceType type);

    void deleteByResourceIdAndResourceTypeAndOwnerId(
        String resourceId, ResourceType type, Long ownerId);
}
```

```java
@Service
@RequiredArgsConstructor
public class ResourceOwnershipService {
    private final DockerResourceRepository resourceRepo;

    public void register(String resourceId, ResourceType type, String name, Long ownerId) {
        DockerResource res = DockerResource.builder()
            .resourceId(resourceId)
            .resourceType(type)
            .resourceName(name)
            .ownerId(ownerId)
            .build();
        resourceRepo.save(res);
    }

    public List<String> getResourceIds(Long userId, ResourceType type) {
        return resourceRepo.findByOwnerIdAndResourceType(userId, type)
            .stream()
            .map(DockerResource::getResourceId)
            .toList();
    }

    public boolean hasAccess(String resourceId, ResourceType type, User user) {
        if (user.getRole() == Role.ADMIN) return true;
        return resourceRepo.existsByResourceIdAndResourceTypeAndOwnerId(
            resourceId, type, user.getId());
    }

    // Borrar ownership de todos los users (usado cuando admin borra imagen)
    @Transactional
    public void unregister(String resourceId, ResourceType type) {
        resourceRepo.deleteByResourceIdAndResourceType(resourceId, type);
    }

    // Borrar ownership de un user concreto (usado si un user "desvincula" un recurso)
    @Transactional
    public void unregisterForUser(String resourceId, ResourceType type, Long ownerId) {
        resourceRepo.deleteByResourceIdAndResourceTypeAndOwnerId(resourceId, type, ownerId);
    }
}
```

### Día 2-3: ContainerService

```java
@Service
@RequiredArgsConstructor
public class ContainerService {
    private final DockerClient dockerClient;
    private final ResourceOwnershipService ownershipService;

    public List<ContainerDto> listContainers(User currentUser, boolean showAll) {
        List<Container> allContainers = dockerClient.listContainersCmd()
            .withShowAll(showAll)
            .exec();

        if (currentUser.getRole() == Role.ADMIN) {
            return allContainers.stream().map(ContainerMapper::toDto).toList();
        }

        List<String> ownedIds = ownershipService
            .getResourceIds(currentUser.getId(), ResourceType.CONTAINER);

        return allContainers.stream()
            .filter(c -> ownedIds.contains(c.getId()))
            .map(ContainerMapper::toDto)
            .toList();
    }

    public ContainerDto createContainer(CreateContainerRequest req, User currentUser) {
        var response = dockerClient.createContainerCmd(req.image())
            .withName(req.name())
            .exec();

        String containerId = response.getId();

        ownershipService.register(
            containerId, ResourceType.CONTAINER, req.name(), currentUser.getId());

        if (req.autoStart()) {
            dockerClient.startContainerCmd(containerId).exec();
        }

        return inspect(containerId, currentUser);
    }

    public ContainerDto inspect(String id, User currentUser) {
        checkAccess(id, currentUser);
        var info = dockerClient.inspectContainerCmd(id).exec();
        return ContainerMapper.toDetailDto(info);
    }

    public void start(String id, User user)   { checkAccess(id, user); dockerClient.startContainerCmd(id).exec(); }
    public void stop(String id, User user)    { checkAccess(id, user); dockerClient.stopContainerCmd(id).exec(); }
    public void restart(String id, User user) { checkAccess(id, user); dockerClient.restartContainerCmd(id).exec(); }

    public void remove(String id, User user) {
        checkAccess(id, user);
        dockerClient.removeContainerCmd(id).withForce(true).exec();
        ownershipService.unregister(id, ResourceType.CONTAINER);
    }

    public String getLogs(String id, User user, int tail) {
        checkAccess(id, user);
        var sb = new StringBuilder();
        try {
            dockerClient.logContainerCmd(id)
                .withStdOut(true).withStdErr(true).withTail(tail)
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

    private void checkAccess(String containerId, User user) {
        if (!ownershipService.hasAccess(containerId, ResourceType.CONTAINER, user)) {
            throw new UnauthorizedException("No tienes acceso a este contenedor");
        }
    }
}
```

### Día 4: ContainerController

```java
@RestController
@RequestMapping("/api/containers")
@RequiredArgsConstructor
public class ContainerController {
    private final ContainerService containerService;

    @GetMapping
    public List<ContainerDto> list(Authentication auth,
                                    @RequestParam(defaultValue = "true") boolean all) {
        return containerService.listContainers((User) auth.getPrincipal(), all);
    }

    @PostMapping
    public ResponseEntity<ContainerDto> create(Authentication auth,
                                                @Valid @RequestBody CreateContainerRequest req) {
        var dto = containerService.createContainer(req, (User) auth.getPrincipal());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/{id}")
    public ContainerDto inspect(Authentication auth, @PathVariable String id) {
        return containerService.inspect(id, (User) auth.getPrincipal());
    }

    @PostMapping("/{id}/start")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void start(Authentication auth, @PathVariable String id) {
        containerService.start(id, (User) auth.getPrincipal());
    }

    @PostMapping("/{id}/stop")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void stop(Authentication auth, @PathVariable String id) {
        containerService.stop(id, (User) auth.getPrincipal());
    }

    @PostMapping("/{id}/restart")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restart(Authentication auth, @PathVariable String id) {
        containerService.restart(id, (User) auth.getPrincipal());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(Authentication auth, @PathVariable String id) {
        containerService.remove(id, (User) auth.getPrincipal());
    }

    @GetMapping("/{id}/logs")
    public String logs(Authentication auth, @PathVariable String id,
                       @RequestParam(defaultValue = "100") int tail) {
        return containerService.getLogs(id, (User) auth.getPrincipal(), tail);
    }
}
```

### Día 5-6: Probar todo el flujo de ownership
- Crear contenedor como User A → verificar en DB → listar como User A (aparece) → listar como User B (no aparece)
- Admin ve todo
- User A no puede start/stop/delete contenedor de User B

### ✅ Checkpoint Fase 2:
- [ ] GET /api/containers → user ve solo los suyos, admin ve todos
- [ ] POST /api/containers → crea en Docker + registra ownership
- [ ] start/stop/restart → solo si es tuyo o eres admin
- [ ] DELETE → elimina de Docker + elimina de docker_resources
- [ ] User A NO puede operar sobre contenedores de User B

---

## FASE 3 — Imágenes, Redes, Volúmenes (Semana 3: 14-20 abr)

> Cubre: RF-07 (volúmenes), RF-08 (redes) + imágenes

### Día 1-2: ImageService + ImageController

```java
@Service
@RequiredArgsConstructor
public class ImageService {
    private final DockerClient dockerClient;
    private final ResourceOwnershipService ownershipService;

    // LISTAR: user ve solo las que pulleó, admin ve todas
    public List<ImageDto> listImages(User currentUser) {
        List<Image> allImages = dockerClient.listImagesCmd().exec();

        if (currentUser.getRole() == Role.ADMIN) {
            return allImages.stream().map(ImageMapper::toDto).toList();
        }

        List<String> ownedIds = ownershipService
            .getResourceIds(currentUser.getId(), ResourceType.IMAGE);

        return allImages.stream()
            .filter(img -> ownedIds.contains(img.getId()))
            .map(ImageMapper::toDto)
            .toList();
    }

    // PULL: cualquier user, registra ownership
    public ImageDto pullImage(PullImageRequest req, User currentUser) {
        try {
            dockerClient.pullImageCmd(req.image())
                .withTag(req.tag() != null ? req.tag() : "latest")
                .exec(new PullImageResultCallback())
                .awaitCompletion();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new DockerOperationException("Pull interrumpido");
        }

        // Obtener el ID de la imagen recién descargada
        var inspectResponse = dockerClient.inspectImageCmd(
            req.image() + ":" + (req.tag() != null ? req.tag() : "latest")
        ).exec();

        String imageId = inspectResponse.getId();
        String imageName = req.image() + ":" + (req.tag() != null ? req.tag() : "latest");

        // Registrar ownership (si ya existe para este user, la constraint lo ignora)
        try {
            ownershipService.register(imageId, ResourceType.IMAGE, imageName, currentUser.getId());
        } catch (DataIntegrityViolationException e) {
            // Ya registrada para este user, ignorar
        }

        return ImageMapper.toDto(inspectResponse);
    }

    // INSPECCIONAR: cualquier user autenticado (necesita ver info de imagen para crear contenedor)
    public ImageDto inspect(String id) {
        var info = dockerClient.inspectImageCmd(id).exec();
        return ImageMapper.toDto(info);
    }

    // BORRAR: solo admin
    public void remove(String id) {
        dockerClient.removeImageCmd(id).exec();
        ownershipService.unregister(id, ResourceType.IMAGE);  // Limpia ownership de TODOS los users
    }

    // BUSCAR en Docker Hub
    public List<SearchResultDto> search(String query) {
        return dockerClient.searchImagesCmd(query).exec()
            .stream()
            .map(ImageMapper::toSearchDto)
            .toList();
    }
}
```

```java
@RestController
@RequestMapping("/api/images")
@RequiredArgsConstructor
public class ImageController {
    private final ImageService imageService;

    @GetMapping
    public List<ImageDto> list(Authentication auth) {
        return imageService.listImages((User) auth.getPrincipal());
    }

    @PostMapping("/pull")
    public ResponseEntity<ImageDto> pull(Authentication auth,
                                          @Valid @RequestBody PullImageRequest req) {
        var dto = imageService.pullImage(req, (User) auth.getPrincipal());
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    @GetMapping("/{id}")
    public ImageDto inspect(@PathVariable String id) {
        return imageService.inspect(id);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable String id) {
        imageService.remove(id);
    }

    @GetMapping("/search")
    public List<SearchResultDto> search(@RequestParam String q) {
        return imageService.search(q);
    }
}
```

DTOs:
```java
public record PullImageRequest(
    @NotBlank String image,   // nginx, postgres, etc.
    String tag                 // latest, 16-alpine, etc. (default: latest)
) {}

public record ImageDto(String id, List<String> tags, Long size, Long created) {}
public record SearchResultDto(String name, String description, int stars, boolean official) {}
```

### Día 3-4: NetworkService + NetworkController
Mismo patrón que contenedores. Nota: las redes default NO se registran en docker_resources.

```java
@Service
@RequiredArgsConstructor
public class NetworkService {
    private final DockerClient dockerClient;
    private final ResourceOwnershipService ownershipService;

    private static final List<String> DEFAULT_NETWORKS = List.of("bridge", "host", "none");

    public List<NetworkDto> listNetworks(User currentUser) {
        List<Network> allNetworks = dockerClient.listNetworksCmd().exec();

        if (currentUser.getRole() == Role.ADMIN) {
            return allNetworks.stream().map(NetworkMapper::toDto).toList();
        }

        List<String> ownedIds = ownershipService
            .getResourceIds(currentUser.getId(), ResourceType.NETWORK);

        return allNetworks.stream()
            .filter(n -> DEFAULT_NETWORKS.contains(n.getName()) || ownedIds.contains(n.getId()))
            .map(NetworkMapper::toDto)
            .toList();
    }

    public NetworkDto createNetwork(CreateNetworkRequest req, User currentUser) {
        var response = dockerClient.createNetworkCmd()
            .withName(req.name())
            .withDriver(req.driver() != null ? req.driver() : "bridge")
            .exec();

        ownershipService.register(
            response.getId(), ResourceType.NETWORK, req.name(), currentUser.getId());

        return inspect(response.getId(), currentUser);
    }

    public NetworkDto inspect(String id, User currentUser) {
        checkAccess(id, currentUser);
        var info = dockerClient.inspectNetworkCmd().withNetworkId(id).exec();
        return NetworkMapper.toDto(info);
    }

    public void remove(String id, User currentUser) {
        checkAccess(id, currentUser);
        dockerClient.removeNetworkCmd(id).exec();
        ownershipService.unregister(id, ResourceType.NETWORK);
    }

    public void connectContainer(String networkId, String containerId, User currentUser) {
        checkAccess(networkId, currentUser);
        dockerClient.connectToNetworkCmd()
            .withNetworkId(networkId)
            .withContainerId(containerId)
            .exec();
    }

    public void disconnectContainer(String networkId, String containerId, User currentUser) {
        checkAccess(networkId, currentUser);
        dockerClient.disconnectFromNetworkCmd()
            .withNetworkId(networkId)
            .withContainerId(containerId)
            .exec();
    }

    private void checkAccess(String networkId, User user) {
        // Las redes default son accesibles para todos
        try {
            var info = dockerClient.inspectNetworkCmd().withNetworkId(networkId).exec();
            if (DEFAULT_NETWORKS.contains(info.getName())) return;
        } catch (Exception ignored) {}

        if (!ownershipService.hasAccess(networkId, ResourceType.NETWORK, user)) {
            throw new UnauthorizedException("No tienes acceso a esta red");
        }
    }
}
```

```
GET    /api/networks                       → Listar (mis redes + default; admin: todas)
POST   /api/networks                       → Crear red
DELETE /api/networks/{id}                  → Eliminar red
GET    /api/networks/{id}                  → Inspeccionar
POST   /api/networks/{id}/connect          → Conectar contenedor
POST   /api/networks/{id}/disconnect       → Desconectar contenedor
```

### Día 5-6: VolumeService + VolumeController
Mismo patrón exacto que redes, pero sin "defaults" (no hay volúmenes por defecto en Docker).

```
GET    /api/volumes                        → Listar (los míos; admin: todos)
POST   /api/volumes                        → Crear volumen
DELETE /api/volumes/{name}                 → Eliminar volumen
GET    /api/volumes/{name}                 → Inspeccionar
```

### ✅ Checkpoint Fase 3:
- [ ] Pull imagen como User A → aparece en su listado, no en el de User B
- [ ] Admin puede listar y borrar cualquier imagen
- [ ] Redes: User A crea red → solo él (y admin) la ve
- [ ] Redes default (bridge, host, none) visibles para todos
- [ ] Volúmenes: mismo comportamiento que redes
- [ ] User normal NO puede borrar imágenes → 403

---

## FASE 4 — Pulido + Errores + Dashboard (Semana 4: 21-27 abr)

### Día 1-2: Manejo de errores robusto

```java
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleBadRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("BAD_REQUEST", e.getMessage()));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleDockerNotFound(NotFoundException e) {
        return ResponseEntity.status(404)
            .body(new ErrorResponse("NOT_FOUND", "Recurso Docker no encontrado"));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public ResponseEntity<ErrorResponse> handleUnauthorized(UnauthorizedException e) {
        return ResponseEntity.status(403)
            .body(new ErrorResponse("FORBIDDEN", e.getMessage()));
    }

    @ExceptionHandler(ConflictException.class)
    public ResponseEntity<ErrorResponse> handleConflict(ConflictException e) {
        return ResponseEntity.status(409)
            .body(new ErrorResponse("CONFLICT", e.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException e) {
        String msg = e.getBindingResult().getFieldErrors().stream()
            .map(f -> f.getField() + ": " + f.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
            .body(new ErrorResponse("VALIDATION_ERROR", msg));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneral(Exception e) {
        return ResponseEntity.status(500)
            .body(new ErrorResponse("INTERNAL_ERROR", "Error interno del servidor"));
    }
}

public record ErrorResponse(String code, String message) {}
```

### Día 3: CORS + Swagger

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOrigins("http://localhost:4200")
            .allowedMethods("*")
            .allowedHeaders("*");
    }
}
```

Swagger UI en: `http://localhost:8080/swagger-ui.html`

### Día 4-5: System + Dashboard

```java
@RestController
@RequestMapping("/api/system")
@RequiredArgsConstructor
public class SystemController {
    private final DockerClient dockerClient;
    private final ContainerService containerService;
    private final ImageService imageService;
    private final NetworkService networkService;
    private final VolumeService volumeService;

    @GetMapping("/info")
    public Info info() {
        return dockerClient.infoCmd().exec();
    }

    @GetMapping("/version")
    public Version version() {
        return dockerClient.versionCmd().exec();
    }

    @GetMapping("/dashboard")
    public DashboardSummaryDto dashboard(Authentication auth) {
        User user = (User) auth.getPrincipal();
        var containers = containerService.listContainers(user, true);
        long running = containers.stream().filter(c -> "running".equals(c.state())).count();

        return new DashboardSummaryDto(
            containers.size(),
            running,
            containers.size() - running,
            imageService.listImages(user).size(),
            networkService.listNetworks(user).size(),
            volumeService.listVolumes(user).size()
        );
    }
}
```

```java
public record DashboardSummaryDto(
    long totalContainers, long running, long stopped,
    long totalImages, long totalNetworks, long totalVolumes
) {}
```

### Día 6: Activity log

```java
GET /api/activity?page=0&size=20 → Log de actividad paginado
```

### ✅ Checkpoint Fase 4:
- [ ] Errores Docker → JSON limpio
- [ ] Swagger UI documenta todos los endpoints
- [ ] Angular conecta sin CORS
- [ ] Dashboard devuelve datos del usuario actual

---

## FASE 5 — Cierre y entrega (28 abr - 4 may)

- [ ] Test end-to-end de todo el flujo
- [ ] Verificar aislamiento entre usuarios
- [ ] Verificar permisos de admin
- [ ] Dockerizar el backend
- [ ] README.md
- [ ] Preparar defensa

### Dockerfile:
```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/docklite-backend-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### docker-compose.yml:
```yaml
services:
  db:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: docklite
      POSTGRES_USER: docklite
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
    ports:
      - "5432:5432"

  backend:
    build: .
    ports:
      - "8080:8080"
    environment:
      DB_PASSWORD: ${DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/docklite
    volumes:
      - /var/run/docker.sock:/var/run/docker.sock
    depends_on:
      - db

volumes:
  pgdata:
```

---

## Catálogo completo de endpoints

### Auth (RF-01, RF-02)
```
POST   /api/auth/register               → Registro
POST   /api/auth/login                  → Login, devuelve JWT
```

### Users (RF-03)
```
GET    /api/users/me                    → Mi perfil
PUT    /api/users/me                    → Actualizar perfil
GET    /api/users                       → Listar usuarios (ADMIN)
```

### Containers (RF-04, RF-05, RF-06)
```
GET    /api/containers                  → Listar (mis contenedores; admin: todos)
POST   /api/containers                  → Crear contenedor
GET    /api/containers/{id}             → Inspeccionar
DELETE /api/containers/{id}             → Eliminar
POST   /api/containers/{id}/start       → Arrancar
POST   /api/containers/{id}/stop        → Parar
POST   /api/containers/{id}/restart     → Reiniciar
GET    /api/containers/{id}/logs        → Logs (?tail=100)
```

### Images
```
GET    /api/images                      → Listar (las que yo pullé; admin: todas)
POST   /api/images/pull                 → Pull imagen (cualquier user)
GET    /api/images/{id}                 → Inspeccionar (cualquier user)
DELETE /api/images/{id}                 → Eliminar (ADMIN)
GET    /api/images/search?q=            → Buscar en Docker Hub
```

### Networks (RF-08)
```
GET    /api/networks                    → Listar (mis redes + default; admin: todas)
POST   /api/networks                    → Crear red
DELETE /api/networks/{id}               → Eliminar red
GET    /api/networks/{id}               → Inspeccionar
POST   /api/networks/{id}/connect       → Conectar contenedor
POST   /api/networks/{id}/disconnect    → Desconectar contenedor
```

### Volumes (RF-07)
```
GET    /api/volumes                     → Listar (mis volúmenes; admin: todos)
POST   /api/volumes                     → Crear volumen
DELETE /api/volumes/{name}              → Eliminar volumen
GET    /api/volumes/{name}              → Inspeccionar
```

### System
```
GET    /api/system/info                 → Docker system info
GET    /api/system/version              → Docker version
GET    /api/system/dashboard            → Resumen para el usuario actual
GET    /api/activity                    → Log de actividad (paginado)
```

---

## Calendario visual

```
Mar 28-31  ░░░░  FASE 0: Aprender Spring Boot
Abr 01-06  ████  FASE 1: Auth + JWT + Users
Abr 07-13  ████  FASE 2: Contenedores + Ownership
Abr 14-20  ████  FASE 3: Imágenes + Redes + Volúmenes
Abr 21-27  ████  FASE 4: Errores + Swagger + Dashboard
Abr 28-04  ████  FASE 5: Cierre, Dockerizar, README
```

---

## Posibles ampliaciones (si sobra tiempo)

- Terminal web (WebSocket + docker exec)
- Monitoring en tiempo real (STOMP + docker stats)
- Docker Compose (upload YAML + up/down)
- Dashboard con gráficas de uso