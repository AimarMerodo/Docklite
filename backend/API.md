# DockLite — API Reference

Documentación de todos los endpoints expuestos por el backend de DockLite. Este documento es el **contrato** entre backend y frontend, y la referencia para integraciones externas.

> Para una versión interactiva (probar peticiones desde el navegador), arranca el backend y abre **`http://localhost:8080/swagger-ui.html`**.

---

## Convenciones generales

### Base path
Todos los endpoints comparten el prefijo:

```
/api/v1
```

### Autenticación

DockLite usa **JWT Bearer tokens**. Cada petición a un endpoint protegido debe incluir:

```
Authorization: Bearer <jwt>
```

El token se obtiene mediante `POST /auth/login` o aceptando una invitación (`POST /invitations/{token}/accept`). Es válido durante **24 horas** y se firma con HS384.

### Niveles de acceso

| Etiqueta | Quién puede acceder |
|---|---|
| **Público** | Sin autenticación. |
| **USER** | Cualquier usuario autenticado (USER o ADMIN). Algunos endpoints aplican filtrado por propiedad. |
| **ADMIN** | Solo usuarios con rol `ADMIN`. |

### Códigos de respuesta más usados

| Código | Significado |
|---|---|
| `200 OK` | Operación exitosa con cuerpo de respuesta. |
| `201 Created` | Recurso creado. |
| `204 No Content` | Operación exitosa sin cuerpo (start/stop/delete…). |
| `400 Bad Request` | Datos de entrada inválidos (Bean Validation o regla de negocio). |
| `401 Unauthorized` | Falta token, token inválido o credenciales incorrectas. |
| `403 Forbidden` | Autenticado pero sin permisos para esta operación o recurso. |
| `404 Not Found` | Recurso (Docker, BBDD o ruta) inexistente. |
| `409 Conflict` | Violación de unicidad (email/username/recurso ya existente). |
| `500 Internal Server Error` | Error inesperado (capturado por el handler genérico). |

### Forma del cuerpo de error

Todos los errores devuelven el mismo formato JSON:

```json
{
  "timestamp": "2026-04-29T10:30:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Validation failed",
  "fields": {
    "email": "debe ser una dirección de correo electrónico con formato correcto",
    "password": "el tamaño debe estar entre 6 y 2147483647"
  }
}
```

El campo `fields` solo aparece en errores `400` derivados de `@Valid`.

---

## 1. Autenticación

Endpoints relacionados con el inicio de sesión.

### `POST /auth/login`

**Acceso:** Público

Inicia sesión con email y contraseña. Devuelve un JWT y la información mínima del usuario.

**Body**
```json
{
  "email": "admin@docklite.local",
  "password": "admin1234"
}
```

**Respuesta `200 OK`**
```json
{
  "token": "eyJhbGciOiJIUzM4NCJ9...",
  "username": "admin",
  "role": "ADMIN"
}
```

**Errores**
- `401` — credenciales incorrectas (mensaje genérico, no distingue email mal de password mal por seguridad).

---

## 2. Invitaciones

DockLite no permite registro abierto. Los nuevos usuarios entran mediante un **link de invitación** generado por un administrador.

### `GET /invitations/{token}`

**Acceso:** Público

Valida una invitación antes de mostrar el formulario de registro al visitante.

**Respuesta `200 OK`**
```json
{
  "usesRemaining": 3,
  "expiresAt": "2026-05-06T15:42:14.993137"
}
```

**Errores**
- `404` — invitación inexistente, cancelada, caducada o agotada.

---

### `POST /invitations/{token}/accept`

**Acceso:** Público

Acepta una invitación: crea el usuario, decrementa el contador de usos y devuelve un JWT (auto-login).

**Body**
```json
{
  "username": "alice",
  "email": "alice@example.com",
  "password": "alice1234"
}
```

Validaciones: `username` 3-50 caracteres, `email` formato válido, `password` mínimo 6 caracteres.

**Respuesta `201 Created`**
```json
{
  "token": "eyJhbGciOiJIUzM4NCJ9...",
  "username": "alice",
  "role": "USER"
}
```

**Errores**
- `400` — validación fallida.
- `404` — invitación no válida.
- `409` — email o username ya registrados.

---

### `POST /admin/invitations`

**Acceso:** ADMIN

Genera un nuevo link de invitación.

**Body** (todos los campos opcionales)
```json
{
  "maxUses": 5,
  "expiresInDays": 7
}
```

Defaults: `maxUses=1`, `expiresInDays=7`. Mínimos: 1.

**Respuesta `201 Created`**
```json
{
  "id": 1,
  "token": "0a6cd41c-0a67-4234-8e35-88e1910ff630",
  "url": "http://localhost:4200/invite/0a6cd41c-0a67-4234-8e35-88e1910ff630",
  "maxUses": 5,
  "usesRemaining": 5,
  "expiresAt": "2026-05-06T15:42:14.993137",
  "cancelled": false,
  "expired": false,
  "exhausted": false,
  "active": true,
  "createdBy": 7,
  "createdAt": "2026-04-29T15:42:14.997140"
}
```

---

### `GET /admin/invitations`

**Acceso:** ADMIN

Lista todas las invitaciones con sus banderas calculadas (`active`, `expired`, `exhausted`).

**Respuesta `200 OK`** — array de objetos como el de creación.

---

### `DELETE /admin/invitations/{id}`

**Acceso:** ADMIN

Cancela una invitación (no la borra de la base de datos, solo marca `cancelled = true`).

**Respuesta:** `204 No Content`

---

## 3. Usuarios

### `GET /users/me`

**Acceso:** USER

Devuelve el perfil del usuario autenticado.

**Respuesta `200 OK`**
```json
{
  "id": 1,
  "username": "admin",
  "email": "admin@docklite.local",
  "role": "ADMIN",
  "createdAt": "2026-04-28T12:00:11.159264"
}
```

---

### `PUT /users/me`

**Acceso:** USER

Cambia la contraseña del usuario autenticado. Requiere la contraseña actual.

**Body**
```json
{
  "currentPassword": "antigua",
  "newPassword": "nueva1234"
}
```

**Respuesta `200 OK`** — perfil actualizado.

**Errores**
- `400` — `newPassword` con menos de 6 caracteres.
- `401` — `currentPassword` incorrecta.

---

### `GET /users`

**Acceso:** ADMIN

Lista todos los usuarios registrados.

**Respuesta `200 OK`** — array de perfiles.

---

## 4. Contenedores

Operaciones sobre contenedores Docker. Aplica el patrón de propiedad: un USER solo ve y opera sus contenedores; un ADMIN ve y opera todos.

### `GET /containers`

**Acceso:** USER

Lista contenedores. Por defecto incluye los parados.

**Query params**
- `all` (boolean, default `true`) — `false` devuelve solo los running.

**Respuesta `200 OK`**
```json
[
  {
    "id": "f3cdd2c78afef7922a454379e1a5c46645f136caad9f2ec416159f3cbed0d8a5",
    "name": "docklite-test-2",
    "image": "nginx:alpine",
    "state": "running",
    "status": "Up 2 minutes"
  }
]
```

---

### `POST /containers`

**Acceso:** USER

Crea un contenedor. Si la imagen no está en local, la **descarga automáticamente** desde Docker Hub antes de crear el contenedor (pull-on-create).

**Body**
```json
{
  "image": "nginx:alpine",
  "name": "my-nginx",
  "autoStart": true
}
```

`name` es opcional (Docker asigna uno aleatorio si no se especifica).

**Respuesta `201 Created`** — DTO del contenedor creado.

**Side effects**
- Inserta fila en `docker_resources` con el usuario actual como `owner_id`.
- Inserta entrada `CREATE` (y `START` si `autoStart=true`) en `activity_log`.

---

### `GET /containers/{id}`

**Acceso:** USER (con propiedad)

Inspecciona un contenedor. El `id` puede ser corto o largo.

**Respuesta `200 OK`** — DTO del contenedor.

**Errores**
- `403` — el usuario no es propietario y no es admin.
- `404` — el contenedor no existe en Docker.

---

### `POST /containers/{id}/start` · `/stop` · `/restart`

**Acceso:** USER (con propiedad)

Cambia el estado de un contenedor.

**Respuesta:** `204 No Content`

**Side effects:** entrada `START` / `STOP` / `RESTART` en `activity_log`.

---

### `DELETE /containers/{id}`

**Acceso:** USER (con propiedad)

Elimina un contenedor (forzado, equivale a `docker rm -f`).

**Respuesta:** `204 No Content`

**Side effects**
- Borra la fila de `docker_resources`.
- Inserta entrada `DELETE` en `activity_log`.

---

### `GET /containers/{id}/logs`

**Acceso:** USER (con propiedad)

Devuelve los logs de un contenedor.

**Query params**
- `tail` (int, default 100) — número de últimas líneas.

**Respuesta `200 OK`** — texto plano con los logs.

---

## 5. Imágenes

Sigue el mismo patrón de propiedad pero con un matiz: una misma imagen puede tener **varios propietarios** (cuando varios usuarios la pullean), gracias a la restricción `UNIQUE(resource_id, resource_type, owner_id)` de la BBDD.

La eliminación está **reservada al admin** porque Docker mantiene una única copia en disco; borrarla afecta a todos los usuarios que la tenían.

### `GET /images`

**Acceso:** USER

Lista imágenes. USER ve solo las que ha pulleado, ADMIN ve todas las del host.

**Respuesta `200 OK`**
```json
[
  {
    "id": "sha256:5616878291a2eed594aee8db4dade5878cf7edcb475e59193904b198d9b830de",
    "tags": ["nginx:alpine"],
    "size": 93475898,
    "created": "1776287944"
  }
]
```

---

### `POST /images/pull`

**Acceso:** USER

Descarga una imagen desde Docker Hub.

**Body**
```json
{
  "image": "busybox",
  "tag": "latest"
}
```

`tag` es opcional (default `latest`).

**Respuesta `201 Created`** — DTO de la imagen ya descargada.

**Side effects**
- Registra ownership en `docker_resources` para el usuario actual.
- Si ya estaba registrada para él, la operación es idempotente.
- Inserta entrada `PULL` en `activity_log`.

---

### `GET /images/{id}`

**Acceso:** USER

Inspecciona una imagen. Acepta el id (sha256) o el tag (`nginx:alpine`).

---

### `DELETE /images/{id}`

**Acceso:** **ADMIN exclusivo**

Elimina una imagen. Borra **todas** las filas de `docker_resources` que la referencian (de todos los usuarios).

**Respuesta:** `204 No Content`

**Errores**
- `403` — el usuario no es admin.
- `404` — la imagen no existe.

---

### `GET /images/search`

**Acceso:** USER

Busca imágenes en Docker Hub.

**Query params**
- `q` — término de búsqueda.

**Respuesta `200 OK`**
```json
[
  {
    "name": "alpine",
    "description": "A minimal Docker image based on Alpine Linux...",
    "stars": 11497,
    "official": true
  }
]
```

---

## 6. Redes

Las redes por defecto de Docker (`bridge`, `host`, `none`) son visibles para todos. Las redes custom siguen el patrón de propiedad estándar.

### `GET /networks`

**Acceso:** USER

Lista redes. USER ve las default + las suyas. ADMIN ve todas.

---

### `POST /networks`

**Acceso:** USER

Crea una red.

**Body**
```json
{
  "name": "my-net",
  "driver": "bridge"
}
```

`driver` es opcional (default `bridge`).

**Respuesta `201 Created`** — DTO de la red.

---

### `GET /networks/{id}`

**Acceso:** USER (con propiedad o red default)

---

### `DELETE /networks/{id}`

**Acceso:** USER (con propiedad)

**Errores**
- `400` — intento de borrar una red default (`bridge`, `host`, `none`).
- `403` — sin propiedad.

---

### `POST /networks/{id}/connect/{containerId}` · `/disconnect/{containerId}`

**Acceso:** USER (con propiedad o red default + propiedad del contenedor)

Conecta o desconecta un contenedor a una red.

**Verificación de doble propiedad:** el usuario debe poder acceder tanto a la red como al contenedor.

**Respuesta:** `204 No Content`

**Side effects:** entrada `CONNECT` o `DISCONNECT` en `activity_log`.

---

## 7. Volúmenes

Los volúmenes en Docker se identifican por **nombre** (no por hash). El campo `resourceId` en `docker_resources` almacena ese nombre.

### `GET /volumes`

**Acceso:** USER

---

### `POST /volumes`

**Acceso:** USER

**Body**
```json
{
  "name": "my-vol",
  "driver": "local"
}
```

`driver` opcional (default `local`).

---

### `GET /volumes/{name}` · `DELETE /volumes/{name}`

**Acceso:** USER (con propiedad)

---

## 8. Sistema y dashboard

Información del Docker daemon y datos agregados para el frontend.

### `GET /system/info`

**Acceso:** USER

Devuelve la información completa del Docker daemon (objeto `Info` de docker-java: versión kernel, recursos, plugins…).

---

### `GET /system/version`

**Acceso:** USER

Devuelve la versión del Docker Engine.

---

### `GET /system/dashboard`

**Acceso:** USER

Resumen agregado para la pantalla principal del frontend. Respeta la propiedad: un USER ve los contadores de **sus** recursos.

**Respuesta `200 OK`**
```json
{
  "totalContainers": 5,
  "running": 3,
  "stopped": 2,
  "totalImages": 7,
  "totalNetworks": 6,
  "totalVolumes": 5
}
```

---

## 9. Histórico de actividad

### `GET /activity`

**Acceso:** USER

Lista paginada del histórico de acciones. USER ve solo las suyas, ADMIN ve todas. Ordenado por `createdAt` descendente por defecto.

**Query params** (Spring Pageable)
- `page` (default `0`)
- `size` (default `20`)
- `sort` (default `createdAt,desc`)

**Respuesta `200 OK`**
```json
{
  "content": [
    {
      "id": 36,
      "userId": 1,
      "resourceId": "sha256:1487d0af5f52b...",
      "resourceType": "IMAGE",
      "action": "DELETE",
      "createdAt": "2026-04-29T14:29:17.078355"
    }
  ],
  "totalElements": 36,
  "totalPages": 2,
  "number": 0,
  "size": 20,
  "first": true,
  "last": false
}
```

**Acciones registradas:** `CREATE`, `START`, `STOP`, `RESTART`, `DELETE`, `PULL`, `CONNECT`, `DISCONNECT`.

---

## Anexo — Resumen de endpoints

| Método | Ruta | Acceso |
|---|---|---|
| POST | `/auth/login` | Público |
| GET | `/invitations/{token}` | Público |
| POST | `/invitations/{token}/accept` | Público |
| GET | `/admin/invitations` | ADMIN |
| POST | `/admin/invitations` | ADMIN |
| DELETE | `/admin/invitations/{id}` | ADMIN |
| GET | `/users/me` | USER |
| PUT | `/users/me` | USER |
| GET | `/users` | ADMIN |
| GET | `/containers` | USER |
| POST | `/containers` | USER |
| GET | `/containers/{id}` | USER (owner) |
| POST | `/containers/{id}/start` | USER (owner) |
| POST | `/containers/{id}/stop` | USER (owner) |
| POST | `/containers/{id}/restart` | USER (owner) |
| DELETE | `/containers/{id}` | USER (owner) |
| GET | `/containers/{id}/logs` | USER (owner) |
| GET | `/images` | USER |
| POST | `/images/pull` | USER |
| GET | `/images/{id}` | USER |
| DELETE | `/images/{id}` | ADMIN |
| GET | `/images/search` | USER |
| GET | `/networks` | USER |
| POST | `/networks` | USER |
| GET | `/networks/{id}` | USER |
| DELETE | `/networks/{id}` | USER (owner) |
| POST | `/networks/{id}/connect/{containerId}` | USER (owner) |
| POST | `/networks/{id}/disconnect/{containerId}` | USER (owner) |
| GET | `/volumes` | USER |
| POST | `/volumes` | USER |
| GET | `/volumes/{name}` | USER (owner) |
| DELETE | `/volumes/{name}` | USER (owner) |
| GET | `/system/info` | USER |
| GET | `/system/version` | USER |
| GET | `/system/dashboard` | USER |
| GET | `/activity` | USER |

**Total: 36 endpoints** organizados en **9 bloques funcionales**.
