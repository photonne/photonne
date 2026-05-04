#!/usr/bin/env bash
# Script de despliegue de Photonne sobre Docker Compose.
#
# Uso:
#   ./scripts/deploy.sh <comando> [servicios...]
#
# Comandos:
#   up              Levanta los servicios en segundo plano (docker compose up -d).
#   down            Detiene y elimina los contenedores (conserva volúmenes).
#   restart         Reinicia los servicios.
#   build           Reconstruye las imágenes locales sin cache.
#   pull            Descarga las imágenes publicadas en el registry.
#   logs            Muestra los logs (sigue en tiempo real con -f).
#   status          Muestra el estado de los servicios (docker compose ps).
#   clean           Elimina contenedores, volúmenes e imágenes del proyecto.
#   purge           Igual que clean, pero también borra ./data (¡destructivo!).
#
# Ejemplos:
#   ./scripts/deploy.sh up
#   ./scripts/deploy.sh up photonne-api
#   ./scripts/deploy.sh logs photonne-ml
#   ./scripts/deploy.sh clean

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${PROJECT_DIR}"

COLOR_RESET='\033[0m'
COLOR_INFO='\033[1;34m'
COLOR_OK='\033[1;32m'
COLOR_WARN='\033[1;33m'
COLOR_ERR='\033[1;31m'

log()  { printf "${COLOR_INFO}[deploy]${COLOR_RESET} %s\n" "$*"; }
ok()   { printf "${COLOR_OK}[deploy]${COLOR_RESET} %s\n" "$*"; }
warn() { printf "${COLOR_WARN}[deploy]${COLOR_RESET} %s\n" "$*"; }
err()  { printf "${COLOR_ERR}[deploy]${COLOR_RESET} %s\n" "$*" >&2; }

usage() {
  sed -n '2,22p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
  exit "${1:-0}"
}

require_docker() {
  if ! command -v docker >/dev/null 2>&1; then
    err "Docker no está instalado o no está en el PATH."
    exit 1
  fi
  if ! docker compose version >/dev/null 2>&1; then
    err "Docker Compose v2 no está disponible (se requiere 'docker compose')."
    exit 1
  fi
}

ensure_env_file() {
  if [[ ! -f .env ]]; then
    if [[ -f .env.example ]]; then
      warn "No se encontró .env. Copiando desde .env.example."
      cp .env.example .env
      warn "Edita .env y rellena los valores marcados con * antes de un despliegue real."
    else
      warn "No se encontró .env ni .env.example; se usarán los valores por defecto del compose."
    fi
  fi
}

confirm() {
  local prompt="$1"
  if [[ "${ASSUME_YES:-0}" == "1" ]]; then
    return 0
  fi
  read -r -p "${prompt} [y/N] " reply
  [[ "${reply}" =~ ^[Yy]$ ]]
}

cmd_up() {
  ensure_env_file
  log "Levantando servicios: ${*:-todos}"
  docker compose up -d "$@"
  ok "Servicios levantados."
  docker compose ps
}

cmd_down() {
  log "Deteniendo servicios..."
  docker compose down "$@"
  ok "Servicios detenidos."
}

cmd_restart() {
  log "Reiniciando servicios: ${*:-todos}"
  docker compose restart "$@"
  ok "Servicios reiniciados."
}

cmd_build() {
  ensure_env_file
  log "Reconstruyendo imágenes (sin cache)..."
  docker compose build --no-cache "$@"
  ok "Build completado."
}

cmd_pull() {
  log "Descargando imágenes desde el registry..."
  docker compose pull "$@"
  ok "Imágenes actualizadas."
}

cmd_logs() {
  docker compose logs -f --tail=200 "$@"
}

cmd_status() {
  docker compose ps
}

cmd_clean() {
  warn "Esta operación eliminará:"
  warn "  - Contenedores del proyecto"
  warn "  - Volúmenes declarados (postgresql_data, pgadmin_data, ml_models)"
  warn "  - Imágenes construidas/descargadas para este compose"
  if ! confirm "¿Continuar?"; then
    log "Operación cancelada."
    return 0
  fi
  log "Eliminando contenedores, volúmenes e imágenes..."
  docker compose down --volumes --rmi all --remove-orphans
  ok "Limpieza completada."
}

cmd_purge() {
  warn "PURGE eliminará también ./data/photos y ./data/thumbnails (datos del host)."
  if ! confirm "¿Estás seguro? Esta acción NO se puede deshacer"; then
    log "Operación cancelada."
    return 0
  fi
  cmd_clean
  if [[ -d ./data ]]; then
    log "Eliminando ./data ..."
    rm -rf ./data
    ok "./data eliminado."
  fi
}

main() {
  if [[ $# -lt 1 ]]; then
    usage 1
  fi

  require_docker

  local command="$1"; shift
  case "${command}" in
    up)       cmd_up "$@" ;;
    down)     cmd_down "$@" ;;
    restart)  cmd_restart "$@" ;;
    build)    cmd_build "$@" ;;
    pull)     cmd_pull "$@" ;;
    logs)     cmd_logs "$@" ;;
    status|ps) cmd_status ;;
    clean)    cmd_clean ;;
    purge)    cmd_purge ;;
    -h|--help|help) usage 0 ;;
    *)
      err "Comando desconocido: ${command}"
      usage 1
      ;;
  esac
}

main "$@"
