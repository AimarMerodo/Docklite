FROM node:20-alpine AS build
WORKDIR /app

COPY package*.json ./
RUN npm install

COPY . .
RUN npm run build


FROM nginx:alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf


COPY --from=build /app/dist/frontend/browser/ /usr/share/nginx/html/
RUN set -eux; \

    subdir="$(find /usr/share/nginx/html -mindepth 1 -maxdepth 1 -type d | head -n 1)"; \
    if [ -n "$subdir" ] && [ -f "$subdir/index.html" ]; then \
      tmp="/tmp/appdist"; \
      mkdir -p "$tmp"; \
      cp -r "$subdir"/* "$tmp"/; \
      rm -rf /usr/share/nginx/html/*; \
      cp -r "$tmp"/* /usr/share/nginx/html/; \
      rm -rf "$tmp"; \
    fi; \

    if [ -d "/usr/share/nginx/html/browser" ]; then \
      cp -r /usr/share/nginx/html/browser/* /usr/share/nginx/html/; \
      rm -rf /usr/share/nginx/html/browser; \
    fi

EXPOSE 80
