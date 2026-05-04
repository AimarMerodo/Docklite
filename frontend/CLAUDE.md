# CLAUDE.md

This file provides guidance to Claude Code when working in the **frontend** of DockLite.

## Project context

`frontend` is the Angular 21 SPA for **DockLite**, a lightweight web platform that lets a server admin self-host Docker container management on a Linux box. It's the final-year project (DAW) of a junior developer, so favor **straightforward, idiomatic Angular** over advanced patterns. Tailwind 4 is the styling system.

The backend is **already complete and tested** — every endpoint listed below is live. Treat the API as the contract: the frontend's job is to consume it cleanly.

The wider repo lives two levels up (`Docklite/`) and contains:

- `backend/` — Spring Boot 4 / Java 21 backend, **finished**.
- `frontend/` — this folder, Angular 21 SPA.
- `docker-compose.yml` + `install.sh` — production deployment stack.
- `Memoria_Docklite.docx` — TFG memoria, mostly done; pending sections (Pruebas, mockups del frontend, diagrama de navegabilidad) are the **only thing left after the frontend is built**.
- `rubrica/` — official evaluation rubrica.

## Common commands

Run from `frontend/`:

```bash
npm install                # install deps
npm start                  # ng serve on http://localhost:4200
npm run build              # production build to dist/
npm test                   # run unit tests (vitest)
```

The dev server (`:4200`) talks to the backend on `:8080`. CORS is enabled in the backend for `http://localhost:4200` only — it's there only for development. In production both run behind nginx as same-origin.

## Deployment model (important)

- The frontend's nginx (in production) **also acts as reverse proxy**: serves the SPA and proxies `/api/*` to the backend on the internal Docker network.
- Result: in production the browser only sees one origin (e.g. `https://docklite.example.com`), there is **no CORS** and no need to know the backend URL.
- The Angular code should call **relative paths** (`/api/v1/...`), never absolute URLs. The dev server handles this either via Angular `proxy.conf.json` or via direct calls; in prod nginx takes over.

## Backend API — what's available

Base path: `/api/v1`. All routes except auth/invitations require `Authorization: Bearer <jwt>`.

### Auth (public)
```
POST /auth/login                         body: { email, password }
                                         → { token, username, role }
```

The endpoint `POST /auth/register` **does not exist** — registration is invitation-only.

### Invitations (public flow + admin-only management)
```
GET  /invitations/{token}                public — validate before showing form
POST /invitations/{token}/accept         public — body: { username, email, password }
                                         → { token, username, role }   (auto-login)

POST   /admin/invitations                ADMIN — body: { maxUses?, expiresInDays? }
GET    /admin/invitations                ADMIN — list all with active/expired/exhausted flags
DELETE /admin/invitations/{id}           ADMIN — cancel
```

### Users
```
GET /users/me                            current user profile
PUT /users/me                            body: { currentPassword, newPassword }
GET /users                               ADMIN — list all
```

### Containers
```
GET    /containers?all=true|false        list (own; admin sees all)
POST   /containers                       body: { image, name?, autoStart }   (auto-pulls if image missing)
GET    /containers/{id}                  inspect
POST   /containers/{id}/start
POST   /containers/{id}/stop
POST   /containers/{id}/restart
DELETE /containers/{id}
GET    /containers/{id}/logs?tail=100    text/plain response
```

### Images
```
GET    /images                           list (own; admin sees all)
POST   /images/pull                      body: { image, tag? }
GET    /images/{id}                      inspect
DELETE /images/{id}                      ADMIN only
GET    /images/search?q=alpine           Docker Hub search
```

### Networks
```
GET    /networks
POST   /networks                         body: { name, driver? }
GET    /networks/{id}
DELETE /networks/{id}                    cannot delete bridge/host/none → 400
POST   /networks/{id}/connect/{containerId}
POST   /networks/{id}/disconnect/{containerId}
```

### Volumes
```
GET    /volumes
POST   /volumes                          body: { name, driver? }
GET    /volumes/{name}
DELETE /volumes/{name}
```

### System & dashboard
```
GET /system/info                         Docker daemon info
GET /system/version                      Docker engine version
GET /system/dashboard                    aggregated counters for the home screen
```

### Activity log
```
GET /activity?page=0&size=20             paginated; admin sees all, user sees own
```

### Error response shape (all 4xx/5xx)
```json
{
  "timestamp": "...",
  "status": 404,
  "error": "Not Found",
  "message": "...",
  "fields": { "email": "..." }   // only on 400 from @Valid
}
```

Swagger UI at `http://localhost:8080/swagger-ui.html` for live exploration.

## Auth flow expected on the frontend

1. **Login** at `/login`: POST `/auth/login`, store JWT in memory + `localStorage`.
2. **HTTP interceptor** adds `Authorization: Bearer <jwt>` to every request to `/api/*`.
3. **Auth guard** redirects unauthenticated users to `/login`.
4. **Role guard** restricts admin-only routes (e.g. `/admin/users`, `/admin/invitations`).
5. **Invitation accept** at `/invite/:token`: validates, shows registration form, auto-login on accept.
6. On 401 from the API: clear token, redirect to `/login`.

## Required screens (per the casos de uso)

- `/login` — login form.
- `/invite/:token` — accept invitation (public).
- `/dashboard` — counters + recent activity.
- `/containers` — list + create + per-row actions (start/stop/restart/delete/logs).
- `/containers/:id` — detail with logs viewer.
- `/images` — list + pull form + Docker Hub search.
- `/networks` — list + create + connect/disconnect to a container.
- `/volumes` — list + create.
- `/admin/invitations` — generate, list, cancel (ADMIN only).
- `/admin/users` — list users (ADMIN only).
- `/me` — profile + change password.

## Stack & conventions

- **Angular 21** (standalone components, no NgModules — modern style).
- **Tailwind 4** for styling.
- **Vitest** for unit tests (`@angular/build` test runner).
- **HttpClient** with interceptors for auth + error handling.
- Use **typed services** (one per resource: `ContainerService`, `ImageService`, `InvitationService`…).
- Use **typed DTOs** (interfaces) that mirror the backend records.

## Memoria pendiente — al terminar el frontend, hay que cerrar:

- **Validación y pruebas** (sección entera): backend ya está listo (.http files + SonarQube), falta frontend (Karma/Jasmine o Vitest, ESLint o SonarQube TS).
- **Diagrama de navegabilidad** (puede ser en PlantUML, mostrando las rutas del SPA y los guards).
- **Capturas de la interfaz** (mockups o screenshots reales) para la sección de Diseño.
- **Capturas de SonarQube** (backend + frontend) en la sección de Pruebas.

## Convenciones del repo

- No usar `console.log` en código de producción.
- No commitear `.env` (está gitignored).
- El backend distribuye los IDs de contenedores en formato largo (sha256:…); el frontend puede mostrar los 12 primeros caracteres como hace `docker ps`.
- Los timestamps del backend vienen en formato ISO-8601 (`Instant.toString()` para `ErrorResponse`, `LocalDateTime` para entidades).
