#!/usr/bin/env bash
set -euo pipefail

BACKUP_DIR="${BACKUP_DIR:-/opt/qoffee/backups}"
COMPOSE_DIR="${COMPOSE_DIR:-/opt/qoffee/backend/deploy}"
RETENTION_DAYS="${RETENTION_DAYS:-14}"

mkdir -p "$BACKUP_DIR"
cd "$COMPOSE_DIR"

if [[ -f "$COMPOSE_DIR/.env" ]]; then
  set -a
  # shellcheck disable=SC1091
  source "$COMPOSE_DIR/.env"
  set +a
fi

timestamp="$(date +%Y%m%d-%H%M%S)"
docker compose exec -T postgres pg_dump -U "$POSTGRES_USER" "$POSTGRES_DB" | gzip > "$BACKUP_DIR/qoffee-$timestamp.sql.gz"
find "$BACKUP_DIR" -type f -name 'qoffee-*.sql.gz' -mtime +"$RETENTION_DAYS" -delete
