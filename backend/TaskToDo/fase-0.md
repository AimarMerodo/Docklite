# Fase 0 — Conectar backend a BBDD y crear el esquema

> **Objetivo:** Spring Boot conecta a Postgres y Flyway crea las 3 tablas (`users`, `docker_resources`, `activity_log`) al arrancar.
>
> **Reglas:** una tarea cada vez. Confirmar antes de pasar a la siguiente.

---

## ☐ Tarea 0.3 — Configurar el datasource en `application.yaml` ECHO

**Objetivo:** que Spring sepa con qué BBDD hablar.

- [ ] Abrir `src/main/resources/application.yaml`.
- [ ] Añadir bajo `spring:` estas claves:
  - `datasource.url` → `jdbc:postgresql://localhost:5432/docklite`
  - `datasource.username` → `docklite`
  - `datasource.password` → `docklitetest`
  - `jpa.hibernate.ddl-auto` → `validate` (Hibernate solo verifica, no crea nada — el esquema lo manda Flyway)
  - `jpa.show-sql` → `true` (para ver las queries en logs durante desarrollo)

**Aviso YAML:** indentación con 2 espacios, nunca tabs.

**Por ahora hardcodeado**, ya lo sacaremos a variables de entorno en una fase posterior.

**Entregable:** `application.yaml` con esas 5 claves.

---

## ☐ Tarea 0.4 — Migración V1: tabla `users` ECHO

**Objetivo:** primera tabla real del proyecto.

- [ ] Crear la carpeta `src/main/resources/db/migration/`.
- [ ] Crear el fichero `V1__create_users.sql` (¡doble guion bajo!).
- [ ] Definir la tabla `users` con estas columnas (ver `plan.md` líneas 51-60 para el SQL exacto):
  - `id` BIGSERIAL PK
  - `username` VARCHAR(50) UNIQUE NOT NULL
  - `email` VARCHAR(255) UNIQUE NOT NULL
  - `password_hash` VARCHAR(255) NOT NULL
  - `role` VARCHAR(20) NOT NULL DEFAULT 'USER'
  - `created_at` TIMESTAMP NOT NULL DEFAULT NOW()
  - `updated_at` TIMESTAMP NOT NULL DEFAULT NOW()

**Concepto:** `BIGSERIAL` = `BIGINT` autoincremental (Postgres se encarga de generar los IDs).

**Entregable:** fichero `.sql` creado con la sentencia `CREATE TABLE`.

---

## ☐ Tarea 0.5 — Migración V2: tabla `docker_resources` echo

**Objetivo:** la tabla que registra qué recursos de Docker pertenecen a qué user.

- [ ] Crear `V2__create_docker_resources.sql` en la misma carpeta.
- [ ] Columnas (ver `plan.md` líneas 62-71):
  - `id` BIGSERIAL PK
  - `resource_id` VARCHAR(64) NOT NULL — el ID que Docker asigna al recurso
  - `resource_type` VARCHAR(20) NOT NULL — `CONTAINER` | `IMAGE` | `VOLUME` | `NETWORK`
  - `resource_name` VARCHAR(255) — nombre legible (ej: `nginx:latest`)
  - `owner_id` BIGINT NOT NULL — FK a `users(id)` con `ON DELETE CASCADE`
  - `created_at` TIMESTAMP NOT NULL DEFAULT NOW()
- [ ] Añadir al final: `UNIQUE(resource_id, resource_type, owner_id)`

**Concepto clave:** ese `UNIQUE` con tres columnas (no solo `resource_id`) es a propósito. Permite que la misma imagen tenga varios "dueños" (porque dos users pueden pullear `nginx:latest` y los dos deben verla en su listado), pero impide que el mismo user la registre dos veces.

**Concepto FK:** `ON DELETE CASCADE` = "si borras un user, borra automáticamente todas sus filas en `docker_resources`". Limpia huérfanos solo.

**Entregable:** fichero `.sql` creado.

---

## ☐ Tarea 0.6 — Migración V3: tabla `activity_log` ECHO

**Objetivo:** registro de auditoría — cada acción mutante de la app dejará una fila aquí.

- [ ] Crear `V3__create_activity_log.sql`.
- [ ] Columnas (ver `plan.md` líneas 73-81):
  - `id` BIGSERIAL PK
  - `user_id` BIGINT NOT NULL — FK a `users(id)` (sin CASCADE: si borras un user no quieres perder el histórico)
  - `resource_id` VARCHAR(64) — opcional
  - `resource_type` VARCHAR(20) — opcional
  - `action` VARCHAR(50) NOT NULL — `CREATE`, `DELETE`, `START`, `STOP`, `PULL`...
  - `created_at` TIMESTAMP NOT NULL DEFAULT NOW()

**Por qué sin CASCADE aquí:** los logs de auditoría sobreviven al borrado de usuarios. Si necesitaras forzar el borrado de un user con histórico, lo manejarás explícitamente en el código.

**Entregable:** fichero `.sql` creado.

---

## ☐ Tarea 0.7 — Arrancar y verificar | echo y funciona

**Objetivo:** ver el ciclo completo Spring → Postgres → Flyway aplica las 3 migraciones.

- [ ] Confirmar que Postgres está vivo: `docker ps` → `docklite-db` `(healthy)`.
- [ ] Ejecutar `./mvnw spring-boot:run`.
- [ ] **Leer los logs en este orden**:
  1. Banner de Spring Boot.
  2. HikariPool: `Start completed` (conexión a Postgres OK).
  3. Flyway: `Successfully validated 3 migrations`.
  4. Flyway: `Migrating schema "public" to version "1 - create users"`, luego `2 - create docker resources`, luego `3 - create activity log`.
  5. Flyway: `Successfully applied 3 migrations`.
  6. `Started DockliteBackendApplication in X seconds` → ✅.

**Errores típicos:**

- `Connection refused` → Postgres no levantado o puerto mal.
- `password authentication failed` → user/pass del yaml mal.
- `Migration V2 failed: relation "users" does not exist` → orden de versiones mal o typo.
- `Validate failed: Migration checksum mismatch` → has editado un `.sql` ya aplicado. Si pasa en esta fase: `docker compose down -v && docker compose up -d db` y vuelve a arrancar (te recrea la BBDD limpia).

Si peta, copia el error literal y me lo pasas — diagnóstico antes de tocar nada.

**Entregable:** log con `Started DockliteBackendApplication`.

---

## ☐ Tarea 0.8 — Verificar las tablas desde la BBDD | echo y funciona

**Objetivo:** cerrar el círculo. No fiarse solo de los logs.

- [ ] Conectarse a Postgres con DBeaver / `docker exec -it docklite-db psql -U docklite -d docklite`.
- [ ] Listar tablas (`\dt` en psql, o el árbol del cliente gráfico). Deben aparecer:
  - `users`
  - `docker_resources`
  - `activity_log`
  - `flyway_schema_history` (la creó Flyway sola)
- [ ] `SELECT version, description, success FROM flyway_schema_history;` → 3 filas, todas con `success=true`.
- [ ] Inspeccionar la estructura de `users` (`\d users` en psql) y verificar que los tipos y constraints coinciden con lo definido.

**Entregable:** confirmar que las 4 tablas existen y las 3 migraciones tienen `success=true`.

---

# ✅ Fin de la Fase 0 | echo y funciona

Al terminar habrás conseguido:

- Spring Boot conectado a Postgres.
- Las 3 tablas del modelo creadas mediante migraciones versionadas.
- Flyway corriendo automáticamente al arrancar.
- Histórico de migraciones registrado en `flyway_schema_history`.

**Siguiente:** Fase 1 — Entidades JPA, repositorios y primer endpoint (registro de usuarios).
