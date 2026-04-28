# Fase 1 — Usuarios, autenticación y JWT

> **Objetivo:** tener endpoints de registro y login que devuelven un JWT, y rutas protegidas que lo validan. Cubre RF-01, RF-02, RF-03.
>
> **Reglas:** una tarea cada vez. Confirmar antes de pasar a la siguiente.
>
> **Referencia:** `plan.md` líneas 280-560 tiene el código de referencia. No copies sin entender — pregunta.

---

## ☐ Tarea 1.1 — Estructura de paquetes

**Objetivo:** preparar las carpetas donde vivirá el código de usuarios y auth.

- [ ] Bajo `src/main/java/es/docklite/docklitebackend/`, crear estos paquetes vacíos:
  - `user/entity`
  - `user/repository`
  - `user/controller`
  - `user/dto`
  - `user/service`
  - `auth/controller`
  - `auth/service`
  - `auth/dto`
  - `auth/jwt`
  - `config`

**Concepto:** organizamos por **feature** (user, auth, config), no por capa técnica (controllers, services...). Es más escalable cuando el proyecto crece.

**Entregable:** paquetes creados. Puedes meter una clase vacía en cada uno para que IntelliJ no los borre.

---

## ☐ Tarea 1.2 — Enum `Role` y entidad `User` echo

**Objetivo:** el primer `@Entity` JPA que mapea a la tabla `users`.

- [ ] En `user/entity/` crear el enum `Role` con dos valores: `USER`, `ADMIN`.
- [ ] En `user/entity/` crear la clase `User`. Ver `plan.md` líneas 290-325 para el código completo.
- [ ] Anotaciones importantes a entender antes de copiar:
  - `@Entity` — le dice a Hibernate "esta clase es una tabla".
  - `@Table(name = "users")` — el nombre exacto de la tabla (sin esto Hibernate buscaría `user`, palabra reservada en SQL).
  - `@Id` + `@GeneratedValue(strategy = IDENTITY)` — la PK autoincremental (equivalente a `BIGSERIAL`).
  - `@Column(name = "password_hash")` — mapeo explícito cuando el campo Java (`passwordHash` camelCase) difiere del de BBDD (`password_hash` snake_case).
  - `@Enumerated(EnumType.STRING)` — guarda el enum como texto (`'USER'`) en vez de un número. Más legible y resistente a reordenar valores.
  - `@PrePersist` / `@PreUpdate` — hooks que Hibernate llama justo antes de hacer INSERT/UPDATE. Los usamos para auto-rellenar `createdAt` / `updatedAt`.
- [ ] Lombok: `@Data @NoArgsConstructor @AllArgsConstructor @Builder`. Genera getters, setters, constructores, toString, equals... gratis.

**Entregable:** `User.java` y `Role.java` creados.

---

## ☐ Tarea 1.3 — `UserRepository` echo

**Objetivo:** crear el repositorio para acceder a la tabla `users` sin escribir SQL.

- [ ] En `user/repository/` crear la interfaz `UserRepository` que extiende `JpaRepository<User, Long>`.
- [ ] Añadir estos métodos (solo la firma, Spring los implementa solo):
  - `Optional<User> findByEmail(String email);`
  - `Optional<User> findByUsername(String username);`
  - `boolean existsByEmail(String email);`
  - `boolean existsByUsername(String username);`

**Concepto:** Spring Data JPA **genera la implementación al vuelo** leyendo el nombre del método. `findByEmail` se traduce a `SELECT * FROM users WHERE email = ?`. Es por eso que los métodos deben llamarse **exactamente** así (`findBy<NombreCampo>`).

**Entregable:** interfaz creada.

---

## ☐ Tarea 1.4 — Arrancar y validar el mapeo

**Objetivo:** comprobar que Hibernate valida que la entidad `User` coincide con la tabla real.

- [ ] `./mvnw spring-boot:run`.
- [ ] Buscar en logs que no haya errores de tipo `Schema-validation: missing column [X] in table [users]`. Eso significa que Hibernate ha revisado tu `@Entity` contra la tabla real y todo cuadra.
- [ ] La app debe llegar a `Started DockliteBackendApplication` sin errores.

**Errores típicos:**
- `Schema-validation: wrong column type` → el tipo Java no coincide con el de BBDD (ej. `String` vs columna `INTEGER`).
- `missing column` → se te ha olvidado mapear un `@Column` o no coincide el nombre.

**Entregable:** app arranca sin errores de validación.

---

## ☐ Tarea 1.5 — Configurar JWT en `application.yaml`

**Objetivo:** añadir las propiedades que necesitará `JwtProvider`.

- [ ] En `application.yaml`, añadir al nivel raíz:
  ```yaml
  jwt:
    secret: unacadenalargadealmenostreintaydosbytesparahmac256
    expiration: 86400000   # 24h en ms
  server:
    port: 8080
  ```

**Concepto:** `jwt.secret` debe tener al menos **32 caracteres** (256 bits) porque `HS256` (el algoritmo que usaremos) lo requiere. Si pones menos, `jjwt` lanzará un error al firmar.

**Más adelante:** sacaremos esto a variables de entorno. Por ahora, hardcodeado.

**Entregable:** yaml con las 3 claves nuevas.

---

## ☐ Tarea 1.6 — `JwtProvider` echo (pendiente Paso 6: getUserIdFromToken + validateToken)

**Objetivo:** clase que **genera** y **valida** JWTs.

- [ ] En `auth/jwt/` crear `JwtProvider` como `@Component`. Código de referencia en `plan.md` líneas 340-384.
- [ ] Métodos que debe tener:
  - `String generateToken(User user)` — construye el JWT con `subject=userId`, claims `username` y `role`, firma con la secret.
  - `Long getUserIdFromToken(String token)` — extrae el `subject` del token.
  - `boolean validateToken(String token)` — intenta parsear; si lanza `JwtException`, devuelve `false`.

**Conceptos JWT a entender antes de copiar:**
- Un JWT tiene 3 partes separadas por puntos: `header.payload.signature`.
- El `payload` son los **claims** (datos): `sub` (subject), `exp` (expiration), más los custom que añadas (`username`, `role`).
- La `signature` se calcula con la secret. Cualquier modificación del payload invalida la firma.
- Por eso **el token no se puede modificar** sin saber la secret, pero **sí se puede leer** (Base64). No metas datos sensibles dentro.

**Entregable:** clase creada, sin errores de compilación.

---

## ☐ Tarea 1.7 — `JwtAuthFilter`

**Objetivo:** filtro que intercepta CADA petición, lee el header `Authorization: Bearer ...`, valida el token y rellena el `SecurityContext`.

- [ ] En `auth/jwt/` crear `JwtAuthFilter` extendiendo `OncePerRequestFilter`. Código de referencia en `plan.md` líneas 386-414.
- [ ] Lógica del método `doFilterInternal`:
  1. Leer header `Authorization`.
  2. Si empieza por `Bearer `, extraer el token.
  3. Validarlo con `jwtProvider`.
  4. Si es válido, buscar el user en BBDD por ID y meterlo en el `SecurityContextHolder` como `Authentication`.
  5. Llamar a `chain.doFilter(...)` para que la petición continúe.

**Concepto:** `OncePerRequestFilter` garantiza que el filtro se ejecuta **una vez por petición**, aunque Spring lo invoque desde varios puntos del pipeline.

**Concepto SecurityContext:** es un "hilo local" (ThreadLocal) donde Spring Security guarda quién es el usuario autenticado de la petición actual. Cuando lo rellenas aquí, el resto del código puede hacer `auth.getPrincipal()` en los controllers sin volver a validar nada.

**Entregable:** clase creada.

---

## ☐ Tarea 1.8 — `SecurityConfig`

**Objetivo:** decirle a Spring Security **qué rutas son públicas**, cuáles requieren auth, y enchufar el filtro JWT.

- [ ] En `config/` crear `SecurityConfig` como `@Configuration @EnableWebSecurity`. Código de referencia en `plan.md` líneas 419-445.
- [ ] Configuración clave:
  - `csrf().disable()` — APIs stateless no lo necesitan (CSRF protege sesiones con cookies).
  - `sessionCreationPolicy(STATELESS)` — no queremos sesiones HTTP, todo va por JWT.
  - `/api/auth/**` → `permitAll()` (registro y login son públicos).
  - Cualquier otra ruta → `authenticated()`.
  - `addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)` — inserta nuestro filtro en el pipeline antes del de auth por usuario/password de Spring.
- [ ] Añadir un `@Bean PasswordEncoder` que devuelva `new BCryptPasswordEncoder()`.

**Concepto BCrypt:** función de hash diseñada para contraseñas. Es **lenta a propósito** (costosa de calcular) y añade **salt automáticamente**, lo que la hace resistente a ataques de diccionario y rainbow tables. Nunca guardes contraseñas en texto plano.

**Entregable:** clase creada, la app sigue arrancando.

---

## ☐ Tarea 1.9 — DTOs de auth

**Objetivo:** definir los objetos de entrada/salida de los endpoints de auth.

- [ ] En `auth/dto/` crear 3 **records** (Java 21 los soporta, más concisos que clases):
  - `RegisterRequest(String username, String email, String password)` con `@NotBlank`, `@Email`, `@Size(min=6)`.
  - `LoginRequest(String email, String password)` con `@NotBlank`.
  - `AuthResponse(String token, String username, String role)`.

**Concepto:** los **records** son inmutables y generan constructor, getters y equals automáticamente. Perfectos para DTOs.

**Concepto validación:** las anotaciones de Bean Validation (`@NotBlank`, `@Email`) se disparan cuando el controller usa `@Valid` en el parámetro. Si fallan, Spring devuelve automáticamente un `400 Bad Request`.

**Entregable:** 3 records creados.

---

## ☐ Tarea 1.10 — `AuthService`

**Objetivo:** lógica de registro y login.

- [ ] En `auth/service/` crear `AuthService` como `@Service`. Código de referencia en `plan.md` líneas 465-500.
- [ ] Dependencias inyectadas (`@RequiredArgsConstructor` de Lombok + campos `final`):
  - `UserRepository`
  - `PasswordEncoder`
  - `JwtProvider`
- [ ] Método `register(RegisterRequest)`:
  1. Verificar que `email` y `username` no existan ya (usando los `existsBy...`).
  2. Construir un `User` con `passwordHash = passwordEncoder.encode(req.password())`.
  3. `userRepository.save(user)`.
  4. Generar token y devolver `AuthResponse`.
- [ ] Método `login(LoginRequest)`:
  1. Buscar user por email; si no existe → error.
  2. Verificar password con `passwordEncoder.matches(rawPassword, user.getPasswordHash())`.
  3. Si coincide, generar token y devolver `AuthResponse`.

**Importante:** nunca digas en el mensaje de error *"email no existe"* o *"contraseña incorrecta"* por separado — da pistas a un atacante. Devuelve siempre `"Credenciales inválidas"`.

**Entregable:** clase creada.

---

## ☐ Tarea 1.11 — `AuthController`

**Objetivo:** exponer los endpoints `POST /api/auth/register` y `POST /api/auth/login`.

- [ ] En `auth/controller/` crear `AuthController` como `@RestController @RequestMapping("/api/auth")`. Código de referencia en `plan.md` líneas 504-519.
- [ ] Métodos:
  - `POST /register` → `@Valid @RequestBody RegisterRequest` → devuelve `201 CREATED` con `AuthResponse`.
  - `POST /login` → `@Valid @RequestBody LoginRequest` → devuelve `200 OK` con `AuthResponse`.

**Entregable:** controller creado, la app arranca.

---

## ☐ Tarea 1.12 — Probar registro y login con Postman

**Objetivo:** verificar que el flujo de auth funciona end-to-end.

- [ ] `./mvnw spring-boot:run`.
- [ ] **Registro:** `POST http://localhost:8080/api/auth/register` con body JSON:
  ```json
  { "username": "test", "email": "test@test.com", "password": "123456" }
  ```
  Respuesta esperada: `201` con `{ "token": "eyJ...", "username": "test", "role": "USER" }`.
- [ ] **Verificar en BBDD:** `SELECT * FROM users;` — debe aparecer la fila con el `password_hash` en BCrypt (empieza por `$2a$`).
- [ ] **Login:** `POST /api/auth/login` con `{ "email": "test@test.com", "password": "123456" }` → `200` con token.
- [ ] **Registro con email duplicado:** debe devolver `400` o `500` (según cómo manejes la excepción — lo pulimos luego).
- [ ] **Login con password mal:** debe devolver error.

**Entregable:** registro y login funcionando, user creado en BBDD.

---

## ☐ Tarea 1.13 — `UserDto` y `UserController`

**Objetivo:** endpoints protegidos para ver/editar el perfil.

- [ ] En `user/dto/` crear el record `UserDto(Long id, String username, String email, String role, LocalDateTime createdAt)` con un método estático `from(User u)`. Ver `plan.md` líneas 553-557.
- [ ] En `user/controller/` crear `UserController` como `@RestController @RequestMapping("/api/users")`. Ver `plan.md` líneas 525-550.
- [ ] Endpoints mínimos por ahora:
  - `GET /me` → devuelve el perfil del user autenticado. El user sale de `Authentication auth` (parámetro inyectado por Spring): `(User) auth.getPrincipal()`.
  - `GET /` con `@PreAuthorize("hasRole('ADMIN')")` → lista todos los usuarios.
- [ ] Para que `@PreAuthorize` funcione, añadir `@EnableMethodSecurity` encima de tu `SecurityConfig`.

**Nota:** el `UserService.findAll()` / `updateProfile()` pueden quedar para después; con `/me` y `/` (listar) es suficiente para cerrar la fase.

**Entregable:** controller creado.

---

## ☐ Tarea 1.14 — Probar las rutas protegidas

**Objetivo:** verificar el ciclo completo de autenticación con JWT.

- [ ] **Sin token:** `GET /api/users/me` → debe devolver `401 Unauthorized`.
- [ ] **Con token (header `Authorization: Bearer <token_del_login>`):** `GET /api/users/me` → `200` con los datos del user.
- [ ] **Con token de un USER normal:** `GET /api/users` → `403 Forbidden`.
- [ ] **Crear un admin manualmente en BBDD** (`UPDATE users SET role = 'ADMIN' WHERE username = 'test';`), hacer login de nuevo para obtener un nuevo token con rol admin, y volver a llamar `GET /api/users` → debe devolver la lista.

**Entregable:** los 4 escenarios pasan.

---

# ✅ Fin de la Fase 1

Al terminar habrás conseguido:

- Entidad `User` mapeada a la tabla `users` (primer `@Entity` real).
- Registro y login funcionando con BCrypt.
- Generación y validación de JWTs.
- Filtro de seguridad que autentica cada petición por el header.
- Rutas públicas (`/api/auth/**`) y protegidas, con control de roles.

**Siguiente:** Fase 2 — Docker: contenedores (listar, crear, arrancar, parar, borrar) con filtrado por ownership.
