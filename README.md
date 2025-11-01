# 🐳 DockLite

**DockLite** es una plataforma web ligera para **desplegar y gestionar aplicaciones en contenedores Docker** desde una interfaz sencilla e intuitiva.

Desarrollado como **Proyecto Final de Grado Superior en Desarrollo de Aplicaciones Web (DAW)**.

---

## 🚀 Descripción del proyecto

DockLite permite:

- Registrar y gestionar aplicaciones Docker
- Ejecutar despliegues automatizados
- Consultar logs y estado de los contenedores
- Realizar rollback de versiones anteriores
- Aprender conceptos DevOps de forma práctica

---

## 🧩 Tecnologías utilizadas

| Capa | Tecnología | Descripción |
|------|-------------|-------------|
| **Frontend** | AngularJS + Tailwind CSS | Interfaz web moderna y responsive |
| **Backend** | Node.js + Express | API REST que controla contenedores y lógica de negocio |
| **Base de datos** | PostgreSQL + Prisma (opcional) | Gestión de datos de usuarios, apps y despliegues |
| **Infraestructura** | Docker + Docker Compose | Contenerización y orquestación de servicios |

---

## ⚙️ Instalación y ejecución local

### 1️⃣ Clonar el repositorio
```bash
git clone https://github.com/tuusuario/docklite.git
cd docklite
```

### 2️⃣ Configurar entorno

Copia el archivo `.env.example` a `.env` y ajusta los valores según tu entorno.

### 3️⃣ Levantar el entorno con Docker Compose

Asegúrate de tener Docker y Docker Compose instalados:
```bash
docker compose up --build
```

Esto iniciará:

- **API** en `http://localhost:3000`
- **Frontend** en `http://localhost:8080`
- **PostgreSQL** en el puerto `5432`

---

## 🧪 Funcionalidades principales

- 🔐 Autenticación de usuarios
- ⚙️ Gestión de aplicaciones
- 🚀 Despliegue de contenedores Docker
- 📜 Logs de ejecución
- ⏪ Rollback de versiones

---

## 🧱 Estructura del repositorio
```
docklite/
├── backend/
│   ├── Dockerfile
│   ├── package.json
│   └── src/
│
├── frontend/
│   ├── Dockerfile
│   ├── package.json
│   └── src/
│
├── docker-compose.yml
├── .env.example
└── README.md
```

---

## 🐳 docker-compose.yml
```yaml
version: "3.9"

services:
  # === Backend ===
  api:
    build: ./backend
    container_name: docklite-api
    ports:
      - "3000:3000"
    environment:
      - NODE_ENV=development
      - DATABASE_URL=${DATABASE_URL}
      - JWT_SECRET=${JWT_SECRET}
      - DOCKER_HOST=${DOCKER_HOST}
    volumes:
      - ./backend:/app
      - /var/run/docker.sock:/var/run/docker.sock
    depends_on:
      - db

  # === Base de datos ===
  db:
    image: postgres:16
    container_name: docklite-db
    restart: always
    environment:
      - POSTGRES_USER=${POSTGRES_USER}
      - POSTGRES_PASSWORD=${POSTGRES_PASSWORD}
      - POSTGRES_DB=${POSTGRES_DB}
    volumes:
      - pgdata:/var/lib/postgresql/data
    ports:
      - "5432:5432"

  # === Frontend ===
  frontend:
    build: ./frontend
    container_name: docklite-frontend
    ports:
      - "8080:80"
    depends_on:
      - api

volumes:
  pgdata:
```

---

## ⚙️ .env.example
```bash
# ==== Configuración general ====
NODE_ENV=development
PORT=3000

# ==== Base de datos PostgreSQL ====
POSTGRES_USER=docklite_user
POSTGRES_PASSWORD=docklite_pass
POSTGRES_DB=docklite
DATABASE_URL=postgresql://docklite_user:docklite_pass@db:5432/docklite

# ==== JWT ====
JWT_SECRET=supersecreto123

# ==== Docker config (si la necesitas en el backend) ====
DOCKER_HOST=unix:///var/run/docker.sock
```

---

## 🧑‍💻 Autor

**Nombre:** [Tu nombre completo]

*Proyecto Fin de Grado – Desarrollo de Aplicaciones Web (DAW)*

- **Centro educativo:** [Tu instituto]
- **Año académico:** 2025
- **Email:** [tuemail@ejemplo.com]

---

## 📜 Licencia

MIT License

---

## 🌐 Enlace

Próximamente: https://docklite.es
