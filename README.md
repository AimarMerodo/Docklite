# 🐳 DockLite

**DockLite** es una plataforma web ligera para **desplegar y gestionar contenedores Docker** desde una interfaz sencilla. Está pensada para auto-hospedarse en un servidor Linux propio: el dueño la instala con un comando, invita usuarios y cada uno gestiona los contenedores que crea.

Proyecto Final del Grado Superior **Desarrollo de Aplicaciones Web (DAW)** — IES Comercio, curso 2025/2026.

---

## ⚡ Instalación rápida

Requiere un servidor **Debian 12+** o **Ubuntu 22.04+** con acceso root o `sudo`.

```bash
curl -fsSL https://raw.githubusercontent.com/AimarMerodo/DockLite/main/install.sh | bash
```

El instalador es completamente interactivo:

1. Comprueba el sistema operativo (Debian/Ubuntu).
2. Instala **Docker** y **git** si no están.
3. Clona el repositorio.
4. Pregunta el modo de despliegue:
   - **Dominio** → HTTPS automático con Let's Encrypt.
   - **IP pública** → solo HTTP.
5. Pide los datos del **administrador inicial** (la contraseña se autogenera si la dejas en blanco).
6. Genera secrets aleatorios (`POSTGRES_PASSWORD`, `JWT_SECRET`).
7. Lanza el stack y muestra al final la URL y las credenciales.

### Alternativa — clonar manualmente

```bash
git clone https://github.com/AimarMerodo/DockLite.git
cd DockLite
./install.sh
```

---

## 🏗️ Arquitectura

```
                          INTERNET
                              │
                              ▼  :80 / :443
              ┌────────────── frontend ──────────────┐
              │  nginx + Angular SPA                 │
              │  ─ sirve la app                      │
              │  ─ proxea /api/* al backend          │
              │  ─ termina TLS en HTTPS              │
              └──────────┬───────────────────────────┘
                         │ red interna docker
                         ▼
              ┌──────────────────┐    ┌──────────────┐
              │     backend      │───►│      db      │
              │  Spring Boot     │    │  Postgres 16 │
              │  ─ JWT auth      │    │              │
              │  ─ docker-java   │    └──────────────┘
              └────────┬─────────┘
                       │  /var/run/docker.sock (montado)
                       ▼
              ┌──────────────────┐
              │   Docker daemon  │   (gestiona los contenedores
              │     del host     │    creados por los usuarios)
              └──────────────────┘
```

El **frontend es lo único expuesto al exterior**. Backend y base de datos viven en una red privada de Docker, inaccesibles desde fuera.

---

## 🚦 Después de instalar

1. Abre la URL que te muestra el instalador.
2. Inicia sesión con el admin y la contraseña generada.
3. Crea un **link de invitación** desde *Admin → Invitations*. Configura cuántos usos y cuántos días vale.
4. Comparte el link (Slack, WhatsApp, lo que sea — DockLite no envía emails, eres tú quien comparte).
5. Cada invitado abre el link, elige usuario y contraseña, y entra.

A partir de ahí cada usuario puede:

- Crear, parar, reiniciar y borrar contenedores.
- Pull de imágenes desde Docker Hub.
- Crear redes y volúmenes.
- Conectar contenedores a redes.
- Ver logs en tiempo real.
- **Solo ve sus propios recursos** (el admin lo ve todo).

---

## 🔐 Consideraciones de seguridad

> ⚠️ **El backend tiene control total del Docker daemon del host.**
>
> Para gestionar contenedores, el backend monta `/var/run/docker.sock`. Eso significa que cualquier vulnerabilidad de tipo RCE en el backend equivaldría a **acceso root al servidor**.
>
> **Es aceptable para self-hosted entre admin + usuarios de confianza** (familia, equipo pequeño, alumnos…). **No es apto para entornos públicos abiertos**.
>
> Mitigación futura recomendada: [docker-socket-proxy](https://github.com/Tecnativa/docker-socket-proxy) para filtrar los endpoints de la Docker API que el backend puede usar.

Otras notas:

- El registro **público está desactivado**. Solo el admin puede invitar.
- `JWT_SECRET` y `POSTGRES_PASSWORD` se generan aleatorios por el instalador (256 bits).
- El archivo `.env` se crea con permisos `600` (solo el dueño puede leerlo).
- **Cambia la contraseña del admin en el primer login** si fue auto-generada.

---

## ⚙️ Configuración (`.env`)

| Variable | Default | Qué hace |
|---|---|---|
| `FRONTEND_HTTP_PORT` | `80` | Puerto HTTP publicado al host |
| `FRONTEND_HTTPS_PORT` | `443` | Puerto HTTPS (solo modo dominio) |
| `POSTGRES_DB` / `POSTGRES_USER` | `docklite` | Nombre y user de la BBDD |
| `POSTGRES_PASSWORD` | autogen | Password de la BBDD |
| `JWT_SECRET` | autogen | Clave de firma de los JWT (256 bits) |
| `ADMIN_USERNAME` / `ADMIN_EMAIL` / `ADMIN_PASSWORD` | preguntados | Credenciales del admin inicial |
| `APP_PUBLIC_URL` | preguntado | URL pública usada en los links de invitación |

`.env` está en `.gitignore` y nunca debe commitearse.

---

## 🔧 Operaciones comunes

```bash
# Ver estado de los contenedores
docker compose ps

# Logs en tiempo real
docker compose logs -f
docker compose logs -f backend

# Reiniciar un servicio (ej. tras cambiar config)
docker compose restart backend

# Parar todo
docker compose down

# Parar y borrar también los volúmenes (⚠️ borra la BBDD)
docker compose down -v
```

### Actualizar a una versión nueva

```bash
git pull
docker compose up -d --build
```

Las migraciones de Flyway se aplican automáticamente al arrancar.

### Backup de la base de datos

```bash
docker compose exec db pg_dump -U docklite docklite > backup-$(date +%F).sql
```

### Restaurar un backup

```bash
docker compose exec -T db psql -U docklite -d docklite < backup-2026-01-15.sql
```

### Renovar el certificado HTTPS

Por ahora la renovación de Let's Encrypt es manual:

```bash
docker run --rm -p 80:80 -v "$(pwd)/certs:/etc/letsencrypt" \
    certbot/certbot renew
docker compose restart frontend
```

Recomendado: añade un cron para que se ejecute mensualmente.

---

## 🧩 Stack técnico

| Capa | Tecnología |
|---|---|
| Backend | Java 21 · Spring Boot 4.0 · Spring Security · Spring Data JPA · Flyway |
| Frontend | Angular 21 · TypeScript |
| BBDD | PostgreSQL 16 |
| Reverse proxy | nginx |
| Auth | JWT (jjwt 0.12) · BCrypt |
| Docker control | docker-java 3.4 |
| Docs API | OpenAPI / Swagger UI (`/swagger-ui.html`) |

---

## 🩹 Troubleshooting

**`Permission denied` al ejecutar `docker compose`**
Tu usuario no está en el grupo `docker`. Tras instalarlo:
```bash
sudo usermod -aG docker $USER
newgrp docker
```

**El instalador dice "Port 80 is already in use"**
Algo más en el servidor está usando ese puerto (Apache, nginx del sistema…). Para verlo:
```bash
sudo ss -tlnp | grep ':80 '
```
Detén ese servicio o elige otro puerto cuando el instalador pregunte.

**El backend devuelve 500 al gestionar Docker**
Verifica que `/var/run/docker.sock` existe y es accesible:
```bash
ls -la /var/run/docker.sock
docker compose logs backend | tail -50
```

**No tengo `git` y la instalación falla**
El instalador lo intenta poner solo. Si te dice "git is required", instálalo manual:
```bash
sudo apt-get install -y git
```
Y vuelve a ejecutarlo.

**La página carga pero las llamadas a `/api/*` dan 502**
El backend aún se está inicializando. Comprueba con `docker compose ps` que todos los servicios están `healthy` y `Up`. Espera 30 segundos tras el arranque.

---

## 🧑‍💻 Autor

**Aimar Merodo**

- **Proyecto:** Fin de Grado – Desarrollo de Aplicaciones Web (DAW)
- **Centro:** IES Comercio
- **Curso:** 2025/2026
- **Email:** aimarmerodoa@gmail.com

---

## 📜 Licencia

MIT License — ver [`LICENSE`](LICENSE).
