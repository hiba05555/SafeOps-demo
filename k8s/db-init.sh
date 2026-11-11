#!/usr/bin/env bash
set -Eeuo pipefail

: "${POSTGRES_USER:?POSTGRES_USER must be configured}"
: "${POSTGRES_DB:?POSTGRES_DB must be configured}"
: "${QUEUE_DB_NAME:?QUEUE_DB_NAME must be configured}"
: "${QUEUE_DB_USER:?QUEUE_DB_USER must be configured}"
: "${QUEUE_DB_PASSWORD:?QUEUE_DB_PASSWORD must be configured}"
: "${APPOINTMENTS_DB_NAME:?APPOINTMENTS_DB_NAME must be configured}"
: "${APPOINTMENTS_DB_USER:?APPOINTMENTS_DB_USER must be configured}"
: "${APPOINTMENTS_DB_PASSWORD:?APPOINTMENTS_DB_PASSWORD must be configured}"
: "${PROFILES_DB_NAME:?PROFILES_DB_NAME must be configured}"
: "${PROFILES_DB_USER:?PROFILES_DB_USER must be configured}"
: "${PROFILES_DB_PASSWORD:?PROFILES_DB_PASSWORD must be configured}"

create_service_database() {
  local db_name="$1"
  local db_user="$2"
  local db_password="$3"

  APP_DB_NAME="$db_name" APP_DB_USER="$db_user" APP_DB_PASSWORD="$db_password" \
    psql --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" --set=ON_ERROR_STOP=1 <<'SQL'
\getenv app_db_name APP_DB_NAME
\getenv app_db_user APP_DB_USER
\getenv app_db_password APP_DB_PASSWORD

SELECT format('CREATE ROLE %I WITH LOGIN PASSWORD %L', :'app_db_user', :'app_db_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'app_db_user')
\gexec

SELECT format('ALTER ROLE %I WITH LOGIN PASSWORD %L', :'app_db_user', :'app_db_password')
\gexec

SELECT format('CREATE DATABASE %I OWNER %I', :'app_db_name', :'app_db_user')
WHERE NOT EXISTS (SELECT 1 FROM pg_database WHERE datname = :'app_db_name')
\gexec

SELECT format('ALTER DATABASE %I OWNER TO %I', :'app_db_name', :'app_db_user')
WHERE EXISTS (SELECT 1 FROM pg_database WHERE datname = :'app_db_name')
\gexec
SQL
}

create_service_database "$QUEUE_DB_NAME" "$QUEUE_DB_USER" "$QUEUE_DB_PASSWORD"
create_service_database "$APPOINTMENTS_DB_NAME" "$APPOINTMENTS_DB_USER" "$APPOINTMENTS_DB_PASSWORD"
create_service_database "$PROFILES_DB_NAME" "$PROFILES_DB_USER" "$PROFILES_DB_PASSWORD"
