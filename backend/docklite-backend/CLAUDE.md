# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project context

`docklite-backend` is the Spring Boot 4.0 / Java 21 backend for **DockLite**, a lightweight web platform to deploy and manage Docker applications. It is the final-year project (DAW) for a junior developer learning Spring Boot in parallel — favor straightforward, idiomatic Spring solutions over advanced patterns.

The full backend roadmap, requirements (RF-01..RF-09), DB schema, ownership model, and per-resource permission policy live in `plan.md` at the project root. **Read `plan.md` before designing features** — it is the source of truth for scope and architecture decisions.

The repository root (two levels up) contains the wider DockLite project: an Angular frontend (`frontend/`), a `docker-compose.yml` (currently runs only `db` and `frontend`; the backend service is commented out and runs locally during development), and shared `.env` / `.env.example`.

## Common commands

Run from `backend/docklite-backend/`. Use the Maven wrapper (`./mvnw` on bash, `mvnw.cmd` on cmd):

```bash
./mvnw spring-boot:run                              # run app
./mvnw clean package                                # build jar
./mvnw test                                         # run all tests
./mvnw test -Dtest=ClassName                        # run single test class
./mvnw test -Dtest=ClassName#methodName             # run single test method
./mvnw flyway:migrate                               # apply DB migrations manually (also runs on app start)
```

Postgres must be reachable; start it via the root `docker compose up -d db` using credentials from the root `.env`.

## Architecture

The backend is currently a freshly-generated Spring Boot skeleton (`DockliteBackendApplication.java` + empty `application.yaml`). The intended architecture, per `plan.md`, is:

- **Stack:** Spring Boot 4.0.5, Java 21, Spring Data JPA + PostgreSQL, Flyway migrations, Spring Security with JWT (jjwt 0.12.6), Spring Web MVC, Bean Validation, Lombok. Docker is controlled via `docker-java` 3.4.1 talking to the host Docker daemon.
- **Package root:** `es.docklite.docklitebackend`.
- **Persistence:** Flyway migrations in `src/main/resources/db/migration` (`V1__create_users.sql`, `V2__create_docker_resources.sql`, `V3__create_activity_log.sql`). Schema is defined in `plan.md`.
- **Ownership model (central concept):** Docker is a global daemon, but users must only see their own resources. The pattern is:
  1. Execute the action against Docker via `docker-java`.
  2. Record `(resource_id, resource_type, owner_id)` in `docker_resources`.
  3. On list: `USER` → fetch their owned IDs from DB and filter the Docker response; `ADMIN` → return everything from Docker unfiltered.
  - `UNIQUE(resource_id, resource_type, owner_id)` allows multiple users to "own" the same image (since Docker stores one copy on disk) while keeping containers/volumes/networks single-owner.
- **Permission policy (per `plan.md`):**
  - Containers/Volumes/Networks: list-own / create-any / delete-own (admin overrides).
  - Images: list-own (one DB row per puller), create-any, **delete is admin-only** (to prevent users removing images another user depends on).
  - Default Docker networks (`bridge`, `host`, `none`) are visible to everyone.
  - When attaching a container to a network, verify the user owns/can-see both resources.
- **Auth:** JWT-based; roles `USER` and `ADMIN` stored on `users.role`.
- **Audit:** Every mutating action writes to `activity_log`.

When implementing features, keep controller → service → repository layering and put Docker-daemon interaction behind a dedicated service so ownership filtering and `activity_log` writes happen in one place.
