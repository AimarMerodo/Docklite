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
|------|------------|-------------|
| **Frontend** | Angular + Tailwind CSS | Interfaz web moderna y responsive |
| **Backend** | Node.js + Express | API REST |
| **Base de datos** | PostgreSQL | Persistencia de datos |
| **Infraestructura** | Docker + Docker Compose | Contenerización |

---

## ✅ Requisitos previos

- Git  
- Node.js + npm  
- Docker Desktop (con Docker Compose)

---

## ⚙️ Instalación y ejecución local

### 1️⃣ Clonar el repositorio
```bash
git clone https://github.com/AimarMerodo/DockLite.git
cd docklite
```

### 2️⃣ Configurar variables de entorno
```bash
cp .env.example .env
```

### 3️⃣ Instalar dependencias

#### Backend
```bash
cd backend
npm install
cd ..
```

#### Frontend
```bash
cd frontend
npm install
npm run build
cd ..
```

---

### 4️⃣ Levantar contenedores
```bash
docker compose up -d --build
```
---
## 🔄 Aplicar cambios y redeplegar la aplicación

Para simplificar el mantenimiento del proyecto, se utiliza un **flujo de redepliegue único** que permite aplicar cualquier cambio realizado en el sistema, independientemente de si afecta al código del frontend, backend, variables de entorno o archivos de configuración Docker.

### ♻️ Redepliegue completo (recomendado)

Este procedimiento detiene los contenedores, reconstruye las imágenes sin usar caché y levanta de nuevo todo el entorno:

```bash
docker compose down
docker compose build --no-cache
docker compose up -d
```

---

## 🌐 Servicios

- Frontend: http://localhost  
- Backend: http://localhost:3000  

---

## 🧑‍💻 Autor

**Nombre:** Aimar Merodo

*Proyecto Fin de Grado – Desarrollo de Aplicaciones Web (DAW)*

- **Centro educativo:** IES Comercio
- **Año académico:** 2025/2026
- **Email:** aimarmerodoa@gmail.com

---

## 📜 Licencia

MIT License

---

## 🌐 Enlace

Próximamente: https://docklite.es
