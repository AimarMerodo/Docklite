#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────
# DockLite — interactive installer
#
# Two ways to run this:
#
#   1. Already cloned the repo
#        cd docklite && ./install.sh
#
#   2. One-liner (script will install git and clone the repo for you)
#        curl -fsSL https://raw.githubusercontent.com/aimar/docklite/main/install.sh | bash
#
# No arguments — every option is asked interactively.
# ──────────────────────────────────────────────────────────────

set -euo pipefail

# Update this when you fork/publish:
REPO_URL="https://github.com/AimarMerodo/Docklite.git"

# Allow interactive prompts even when stdin is a pipe (curl | bash).
if [[ ! -t 0 ]]; then
    exec </dev/tty
fi


# ─────────── Pretty output helpers ───────────
RESET="\033[0m"; BOLD="\033[1m"
BLUE="\033[1;34m"; GREEN="\033[1;32m"; YELLOW="\033[1;33m"; RED="\033[1;31m"

log()   { echo -e "${BLUE}[i]${RESET} $*"; }
ok()    { echo -e "${GREEN}[ok]${RESET} $*"; }
warn()  { echo -e "${YELLOW}[!]${RESET} $*"; }
err()   { echo -e "${RED}[x]${RESET} $*" >&2; }
hr()    { echo "──────────────────────────────────────────────"; }


# ─────────── Prompt helpers ───────────
ask_yes_no() {
    # ask_yes_no "Question" [default y|n]
    local prompt="$1" default="${2:-y}" answer
    local hint="[Y/n]"; [[ "$default" =~ ^[nN] ]] && hint="[y/N]"
    while true; do
        read -rp "$prompt $hint: " answer
        answer="${answer:-$default}"
        case "$answer" in
            [yY]|[yY][eE][sS]) return 0 ;;
            [nN]|[nN][oO])     return 1 ;;
        esac
    done
}

ask_input() {
    # ask_input "Question" [default]   → echoes the answer
    local prompt="$1" default="${2:-}" answer
    if [[ -n "$default" ]]; then
        read -rp "$prompt [$default]: " answer
        echo "${answer:-$default}"
    else
        while [[ -z "${answer:-}" ]]; do read -rp "$prompt: " answer; done
        echo "$answer"
    fi
}

ask_secret() {
    # ask_secret "Question (blank = autogenerate)"
    local prompt="$1" answer
    read -rsp "$prompt: " answer
    echo
    echo "$answer"
}

generate_secret() { openssl rand -hex 32; }                       # 64 hex chars (256 bits)
generate_password() { openssl rand -base64 12 | tr -d '=+/' | cut -c1-12; }


# ─────────── Privilege check ───────────
SUDO=""
ensure_privileges() {
    if [[ $EUID -eq 0 ]]; then
        SUDO=""   # already root
        return
    fi
    if command -v sudo >/dev/null 2>&1; then
        SUDO="sudo"
        log "You'll be asked for your sudo password when needed."
        return
    fi
    err "This script needs root privileges (or sudo) to install packages."
    err "Run it as root, or install sudo and try again."
    exit 1
}


# ─────────── Basic tooling (curl/openssl/envsubst) ───────────
# install_docker apt-get's curl & friends, but the script needs them
# earlier (IP detection, secret generation) and on hosts that already
# have Docker we'd skip install_docker entirely. Make sure they exist
# unconditionally so the script doesn't fail half-way through.
ensure_basic_tools() {
    local missing=()
    command -v curl     >/dev/null 2>&1 || missing+=(curl)
    command -v openssl  >/dev/null 2>&1 || missing+=(openssl)
    # envsubst lives in gettext-base on Debian/Ubuntu.
    command -v envsubst >/dev/null 2>&1 || missing+=(gettext-base)

    if (( ${#missing[@]} == 0 )); then
        return
    fi
    log "Installing required tooling: ${missing[*]}"
    $SUDO apt-get update -y
    $SUDO apt-get install -y "${missing[@]}"
}


# ─────────── OS detection ───────────
OS_ID=""; OS_CODENAME=""; OS_PRETTY=""
detect_os() {
    if [[ ! -f /etc/os-release ]]; then
        err "Cannot detect the OS (no /etc/os-release found)."
        exit 1
    fi
    # shellcheck disable=SC1091
    . /etc/os-release
    OS_ID="${ID:-unknown}"
    OS_CODENAME="${VERSION_CODENAME:-unknown}"
    OS_PRETTY="${PRETTY_NAME:-$OS_ID}"

    case "$OS_ID" in
        ubuntu|debian)
            ok "Detected OS: $OS_PRETTY ($OS_CODENAME)"
            ;;
        *)
            err "Unsupported OS: $OS_PRETTY"
            err "DockLite installer currently supports Debian and Ubuntu only."
            err "Install Docker manually following https://docs.docker.com/engine/install/"
            err "and re-run this script."
            exit 1
            ;;
    esac
}


# ─────────── Source code (clone if not present) ───────────
ensure_repo() {
    if [[ -f docker-compose.yml && -d backend && -d frontend && -f install.sh ]]; then
        ok "Running from an existing DockLite checkout: $PWD"
        return
    fi

    log "DockLite source code is not in the current directory."
    log "I'll fetch it from $REPO_URL"

    if ! command -v git >/dev/null 2>&1; then
        warn "git is not installed."
        if ask_yes_no "Install git now?"; then
            $SUDO apt-get update -y
            $SUDO apt-get install -y git
        else
            err "git is required to fetch DockLite. Aborting."
            exit 1
        fi
    fi

    local target_dir
    target_dir=$(ask_input "Where to install DockLite?" "$PWD/docklite")

    if [[ -d "$target_dir" ]]; then
        if [[ -f "$target_dir/docker-compose.yml" && -d "$target_dir/backend" && -d "$target_dir/frontend" ]]; then
            warn "An existing DockLite checkout was found at $target_dir."
            log "Reinstalling cleanly is the safest option — your data is in"
            log "Docker volumes, not in this directory, so a fresh clone keeps"
            log "your databases and contents intact."
            if ask_yes_no "Wipe $target_dir and re-clone the latest version?" y; then
                # Stop any stack still running from the old checkout so it
                # doesn't fight with us over the docker daemon while we
                # rebuild.
                ( cd "$target_dir" && docker compose down 2>/dev/null || true )
                $SUDO rm -rf "$target_dir"
                git clone "$REPO_URL" "$target_dir"
                ok "Re-cloned fresh."
            else
                log "Reusing existing checkout."
            fi
        else
            err "Directory $target_dir exists but doesn't look like DockLite."
            err "Move/remove it or pick a different path."
            exit 1
        fi
    else
        git clone "$REPO_URL" "$target_dir"
    fi

    cd "$target_dir"
    ok "Working from $PWD"
}


# ─────────── Docker presence + install ───────────
docker_ready() {
    command -v docker >/dev/null 2>&1 \
        && docker compose version >/dev/null 2>&1 \
        && docker info >/dev/null 2>&1
}

install_docker() {
    log "Installing Docker Engine + Compose plugin (this takes a minute)..."

    $SUDO apt-get update -y
    $SUDO apt-get install -y ca-certificates curl gnupg

    $SUDO install -m 0755 -d /etc/apt/keyrings
    $SUDO curl -fsSL "https://download.docker.com/linux/$OS_ID/gpg" \
        -o /etc/apt/keyrings/docker.asc
    $SUDO chmod a+r /etc/apt/keyrings/docker.asc

    local arch
    arch="$(dpkg --print-architecture)"
    echo "deb [arch=$arch signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/$OS_ID $OS_CODENAME stable" \
        | $SUDO tee /etc/apt/sources.list.d/docker.list > /dev/null

    $SUDO apt-get update -y
    $SUDO apt-get install -y \
        docker-ce docker-ce-cli containerd.io \
        docker-buildx-plugin docker-compose-plugin

    $SUDO systemctl enable --now docker

    # Add invoking user to docker group (only if not already root)
    if [[ $EUID -ne 0 ]]; then
        $SUDO usermod -aG docker "$USER"
        ok "Docker installed."
        warn "User '$USER' was added to the 'docker' group, but the change is not"
        warn "active in this shell. Log out and back in (or run 'newgrp docker'),"
        warn "then re-run this script:    ./install.sh"
        exit 0
    fi
    ok "Docker installed."
}

ensure_docker() {
    if docker_ready; then
        ok "Docker is installed and accessible."
        return
    fi
    if ! command -v docker >/dev/null 2>&1; then
        warn "Docker is not installed."
        if ask_yes_no "Install Docker now?"; then
            install_docker
        else
            err "Docker is required. Aborting."
            exit 1
        fi
    else
        # docker exists but the user can't talk to the daemon
        err "Docker is installed but not accessible from this shell."
        err "Make sure your user is in the 'docker' group and the session is refreshed:"
        err "    sudo usermod -aG docker $USER && newgrp docker"
        err "Then re-run this script."
        exit 1
    fi
}


# ─────────── Port availability ───────────
port_in_use() {
    # port_in_use 80  → returns 0 if something is listening on that port
    local port="$1"
    if command -v ss >/dev/null 2>&1; then
        ss -tln 2>/dev/null | awk '{print $4}' | grep -qE "[:.]$port\$"
    elif command -v netstat >/dev/null 2>&1; then
        netstat -tln 2>/dev/null | awk '{print $4}' | grep -qE "[:.]$port\$"
    else
        return 1   # unable to check; assume free
    fi
}


# ─────────── Configuration prompts ───────────
DEPLOY_MODE="" SERVER_NAME="" LE_EMAIL="" APP_PUBLIC_URL=""
FRONTEND_HTTP_PORT="" FRONTEND_HTTPS_PORT=""
ADMIN_USERNAME="" ADMIN_EMAIL="" ADMIN_PASSWORD="" GENERATED_PASSWORD=false

ask_deployment_mode() {
    # Port 80 has to be free for Let's Encrypt's HTTP-01 challenge. If it's
    # already taken (very common: server has nginx/Caddy/NPM in front),
    # certbot can't bind, so we skip the Let's Encrypt path and let DockLite
    # run as a plain-HTTP backend that the existing reverse proxy can forward
    # to with its own TLS termination.
    local has_existing_proxy=false
    if port_in_use 80; then
        has_existing_proxy=true
    fi

    echo
    log "Deployment mode"
    if $has_existing_proxy; then
        warn "Port 80 is in use on this server — usually means there's already a"
        warn "reverse proxy (nginx, Caddy, NPM, Traefik…). The Let's Encrypt"
        warn "option needs port 80 free, so it's disabled."
        echo "  1) Behind reverse proxy  → DockLite as plain HTTP on a custom port"
        echo "                              (your existing proxy terminates TLS)"
        echo "  2) Public IP only         → HTTP (no certificate, no proxy)"
        while true; do
            read -rp "Choose [1-2]: " choice
            case "$choice" in
                1)
                    DEPLOY_MODE="proxy"
                    SERVER_NAME=$(ask_input "Public domain (used in invitation links, e.g. docklite.example.com)")
                    # Pick a high port the user is unlikely to have busy.
                    APP_PUBLIC_URL="https://$SERVER_NAME"
                    return ;;
                2)
                    DEPLOY_MODE="ip"
                    local detected
                    detected="$(curl -fsS --max-time 3 https://api.ipify.org 2>/dev/null || echo)"
                    SERVER_NAME=$(ask_input "Public IP" "$detected")
                    APP_PUBLIC_URL="http://$SERVER_NAME"
                    return ;;
            esac
        done
    fi

    echo "  1) Domain  → HTTPS via Let's Encrypt (recommended)"
    echo "  2) Public IP only  → HTTP (no certificate)"
    while true; do
        read -rp "Choose [1-2]: " choice
        case "$choice" in
            1)
                DEPLOY_MODE="domain"
                SERVER_NAME=$(ask_input "Domain (e.g. docklite.example.com)")
                LE_EMAIL=$(ask_input  "Email for Let's Encrypt notifications")
                APP_PUBLIC_URL="https://$SERVER_NAME"
                return ;;
            2)
                DEPLOY_MODE="ip"
                local detected
                detected="$(curl -fsS --max-time 3 https://api.ipify.org 2>/dev/null || echo)"
                SERVER_NAME=$(ask_input "Public IP" "$detected")
                APP_PUBLIC_URL="http://$SERVER_NAME"
                return ;;
        esac
    done
}

ask_admin_account() {
    echo
    log "Bootstrap admin account (created on first start)"
    ADMIN_USERNAME=$(ask_input "Username" "admin")
    local default_email="admin@${SERVER_NAME}"
    ADMIN_EMAIL=$(ask_input "Email" "$default_email")

    ADMIN_PASSWORD=$(ask_secret "Password (leave blank to auto-generate)")
    if [[ -z "$ADMIN_PASSWORD" ]]; then
        ADMIN_PASSWORD="$(generate_password)"
        GENERATED_PASSWORD=true
        ok "An admin password has been auto-generated."
    fi
}

ask_ports() {
    echo
    if [[ "$DEPLOY_MODE" == "proxy" ]]; then
        log "DockLite HTTP port — your existing reverse proxy will forward here"
        # Default to 8080 — easier to remember than the next free random port.
        local default_port=8080
        if port_in_use "$default_port"; then default_port=8081; fi
        if port_in_use "$default_port"; then default_port=8082; fi
        while true; do
            FRONTEND_HTTP_PORT=$(ask_input "HTTP port" "$default_port")
            if port_in_use "$FRONTEND_HTTP_PORT"; then
                warn "Port $FRONTEND_HTTP_PORT is already in use."
                ask_yes_no "Pick a different port?" && continue
            fi
            break
        done
        # Don't expose 443 in proxy mode — the upstream proxy owns it.
        FRONTEND_HTTPS_PORT="443"
        return
    fi

    log "Host ports for the frontend (the only thing exposed publicly)"
    while true; do
        FRONTEND_HTTP_PORT=$(ask_input "HTTP port" "80")
        if port_in_use "$FRONTEND_HTTP_PORT"; then
            warn "Port $FRONTEND_HTTP_PORT is already in use."
            ask_yes_no "Pick a different port?" && continue
        fi
        break
    done
    if [[ "$DEPLOY_MODE" == "domain" ]]; then
        while true; do
            FRONTEND_HTTPS_PORT=$(ask_input "HTTPS port" "443")
            if port_in_use "$FRONTEND_HTTPS_PORT"; then
                warn "Port $FRONTEND_HTTPS_PORT is already in use."
                ask_yes_no "Pick a different port?" && continue
            fi
            break
        done
    else
        FRONTEND_HTTPS_PORT="443"
    fi
}


# ─────────── .env generation ───────────
write_env() {
    local pg_password jwt_secret
    pg_password="$(generate_secret)"
    jwt_secret="$(generate_secret)"

    cat > .env <<EOF
# Generated by install.sh on $(date -Iseconds)
# Do not commit this file.

# --- Host ports ---
FRONTEND_HTTP_PORT=$FRONTEND_HTTP_PORT
FRONTEND_HTTPS_PORT=$FRONTEND_HTTPS_PORT

# --- Postgres ---
POSTGRES_DB=docklite
POSTGRES_USER=docklite
POSTGRES_PASSWORD=$pg_password

# --- Backend ---
JWT_SECRET=$jwt_secret

# --- Bootstrap admin ---
ADMIN_USERNAME=$ADMIN_USERNAME
ADMIN_EMAIL=$ADMIN_EMAIL
ADMIN_PASSWORD=$ADMIN_PASSWORD

# --- Public URL (used to build invitation links) ---
APP_PUBLIC_URL=$APP_PUBLIC_URL
EOF

    chmod 600 .env
    ok ".env written (mode 600)."
}


# ─────────── HTTPS / certbot ───────────
obtain_certificate() {
    log "Obtaining a Let's Encrypt certificate for $SERVER_NAME..."
    log "Make sure $SERVER_NAME points to this server's public IP and ports 80/443 are reachable."

    if ! ask_yes_no "Continue?" y; then
        err "Aborted by user."
        exit 1
    fi

    mkdir -p ./certs

    # Standalone mode: certbot binds to :80 itself, so frontend can't be running yet.
    docker run --rm \
        -p 80:80 \
        -v "$(pwd)/certs:/etc/letsencrypt" \
        certbot/certbot certonly \
        --standalone \
        --non-interactive \
        --agree-tos \
        --email "$LE_EMAIL" \
        -d "$SERVER_NAME"

    ok "Certificate obtained at ./certs/live/$SERVER_NAME/"
}

generate_https_nginx_conf() {
    log "Generating nginx config from default-ssl.conf.template..."
    SERVER_NAME="$SERVER_NAME" \
        envsubst '${SERVER_NAME}' \
        < ./frontend/nginx/default-ssl.conf.template \
        > ./nginx-active.conf
    ok "nginx-active.conf generated."
}


# ─────────── Stack orchestration ───────────
deploy_stack() {
    log "Building and starting the stack..."
    case "$DEPLOY_MODE" in
        domain)
            docker compose -f docker-compose.yml -f docker-compose.https.yml up -d --build
            ;;
        proxy)
            docker compose -f docker-compose.yml -f docker-compose.proxy.yml up -d --build
            ;;
        *)
            docker compose up -d --build
            ;;
    esac

    log "Waiting for backend to become healthy..."
    local tries=60   # 60 * 2s = 2 min cap, enough for first DB schema + admin bootstrap
    local status=""
    while (( tries > 0 )); do
        # Use the container's built-in healthcheck instead of curl-ing
        # /api/v1/auth/login: that endpoint returns 401 on bogus creds,
        # which curl -f would treat as failure and never break the loop.
        status="$(docker inspect --format '{{.State.Health.Status}}' docklite-backend 2>/dev/null || true)"
        if [[ "$status" == "healthy" ]]; then
            break
        fi
        if [[ "$status" == "unhealthy" ]]; then
            err "Backend container went unhealthy. Check logs:"
            err "    docker compose logs backend --tail=100"
            exit 1
        fi
        sleep 2
        ((tries--))
    done
    if [[ "$status" != "healthy" ]]; then
        warn "Backend did not become healthy within 2 minutes."
        warn "It might still come up — check 'docker compose logs backend' if not."
    else
        ok "Backend healthy."
    fi
    ok "Stack started."
}


# ─────────── Reverse-proxy guidance (proxy mode) ───────────
print_proxy_guidance() {
    echo
    hr
    log "DockLite is running on http://localhost:$FRONTEND_HTTP_PORT"
    log "To make it reachable at $APP_PUBLIC_URL, add a vhost to your"
    log "existing reverse proxy. Examples:"
    echo
    cat <<EOF
  ── nginx ──────────────────────────────────────────────────────
  server {
      listen 443 ssl;
      server_name $SERVER_NAME;
      ssl_certificate     /path/to/fullchain.pem;
      ssl_certificate_key /path/to/privkey.pem;

      location / {
          proxy_pass http://127.0.0.1:$FRONTEND_HTTP_PORT;
          proxy_http_version 1.1;
          proxy_set_header Host \$host;
          proxy_set_header X-Real-IP \$remote_addr;
          proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
          proxy_set_header X-Forwarded-Proto \$scheme;
      }
  }

  ── Caddy ──────────────────────────────────────────────────────
  $SERVER_NAME {
      reverse_proxy 127.0.0.1:$FRONTEND_HTTP_PORT
  }

  ── Nginx Proxy Manager ────────────────────────────────────────
  Add a Proxy Host:
      Domain Names:   $SERVER_NAME
      Forward to:     127.0.0.1   port $FRONTEND_HTTP_PORT
      SSL: request Let's Encrypt cert
EOF
    echo
    hr
}


# ─────────── Final summary ───────────
print_summary() {
    echo
    hr
    ok "DockLite is up and running!"
    hr
    echo
    echo -e "  ${BOLD}URL:${RESET}      $APP_PUBLIC_URL"
    echo -e "  ${BOLD}Admin:${RESET}    $ADMIN_USERNAME  <$ADMIN_EMAIL>"
    if [[ "$GENERATED_PASSWORD" == "true" ]]; then
        echo -e "  ${BOLD}Password:${RESET} ${YELLOW}$ADMIN_PASSWORD${RESET}   ${YELLOW}(save it now — won't be shown again)${RESET}"
    else
        echo -e "  ${BOLD}Password:${RESET} <as you typed it>"
    fi
    echo
    echo "  Useful commands:"
    echo "    docker compose ps        # status"
    echo "    docker compose logs -f   # logs"
    echo "    docker compose down      # stop"
    echo
    if [[ "$DEPLOY_MODE" == "domain" ]]; then
        warn "Cert renewal is NOT yet automated. To renew manually run:"
        echo  "    docker run --rm -p 80:80 -v \"\$(pwd)/certs:/etc/letsencrypt\" \\"
        echo  "      certbot/certbot renew && docker compose restart frontend"
    fi
    echo
}


# ─────────── Main ───────────
main() {
    # Wipe the terminal so the installer's output starts on a clean canvas,
    # uncluttered by whatever scrollback was there before (apt updates,
    # ssh banner, motd, etc.).
    clear 2>/dev/null || printf '\033[2J\033[H'

    hr
    echo -e "${BOLD}DockLite — interactive installer${RESET}"
    hr

    ensure_privileges
    detect_os
    ensure_basic_tools
    ensure_repo

    if [[ -f .env ]]; then
        warn "An existing .env file was found in this directory."
        if ! ask_yes_no "Overwrite it and reconfigure DockLite?" n; then
            err "Aborted. Existing configuration kept untouched."
            exit 0
        fi
    fi

    ensure_docker

    ask_deployment_mode
    ask_admin_account
    ask_ports

    write_env

    if [[ "$DEPLOY_MODE" == "domain" ]]; then
        obtain_certificate
        generate_https_nginx_conf
    fi

    deploy_stack

    if [[ "$DEPLOY_MODE" == "proxy" ]]; then
        print_proxy_guidance
    fi
    print_summary
}

main
