#!/usr/bin/env bash
# Brings Keycloak to the state the Tiko stack expects, from nothing, every time.
#
#   realm "tiko"           registration on, realm roles client / agent / admin, "client" by default
#   client "frontend"      public, Authorization Code + PKCE (S256) only - used by keycloak-js
#   client "tiko-backend"  confidential, service account - the api-gateway's own client
#   audience "tiko-api"    added to both clients' access tokens; the gateway requires it
#   users                  client / agent / admin test accounts (passwords from .env)
#
# Then it reads tiko-backend's generated secret back from Keycloak and writes it to
# $OUT_DIR/gateway.properties, which the gateway loads through spring.config.import.
# No secret lives in git, in an image or in docker-compose.yml.
#
# Idempotent: each object is created when missing, then updated to the desired state, so it
# is safe to re-run (docker compose up re-runs it) and safe against a persisted Keycloak.
set -euo pipefail

KC_URL="${KC_URL:-http://keycloak:8080}"
REALM="${REALM:-tiko}"
OUT_DIR="${OUT_DIR:-/bootstrap}"
# comma separated origins the frontend is served from
FRONTEND_ORIGINS="${FRONTEND_ORIGINS:-http://localhost:3000,http://localhost:5173}"
POST_LOGOUT_REDIRECT_URIS="${FRONTEND_ORIGINS//,/\/\*##}"
API_AUDIENCE="${API_AUDIENCE:-tiko-api}"
BACKEND_CLIENT_ID="${BACKEND_CLIENT_ID:-tiko-backend}"
BACKEND_CLIENT_SECRET="${BACKEND_CLIENT_SECRET:-}"
# uid the gateway runs as (backend/api-gateway/Dockerfile): the only reader of the secret
SECRET_OWNER_UID="${SECRET_OWNER_UID:-10001}"

KCADM=/opt/keycloak/bin/kcadm.sh
KC_CONFIG=/tmp/kcadm.config
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

kc() { "$KCADM" "$@" --config "$KC_CONFIG"; }
log() { echo "[keycloak-init] $*"; }

# ------------------------------------------------------------------ login (waits for Keycloak)
for attempt in $(seq 1 60); do
  if "$KCADM" config credentials --config "$KC_CONFIG" --server "$KC_URL" --realm master \
       --user "$KC_ADMIN" --password "$KC_ADMIN_PASSWORD" >/dev/null 2>&1; then
    break
  fi
  [ "$attempt" = 60 ] && { log "Keycloak not reachable at $KC_URL"; exit 1; }
  log "waiting for Keycloak ($attempt)..."
  sleep 3
done
log "logged in to $KC_URL"

# ------------------------------------------------------------------ helpers
client_uuid() {
  kc get clients -r "$REALM" -q "clientId=$1" --fields id --format csv --noquotes | head -n1 | tr -d '\r'
}

# "a,b" -> ["a/*","b/*"]   (suffix appended to each entry)
json_list() {
  local IFS=',' out="" item
  for item in $1; do out="$out\"$item$2\","; done
  echo "[${out%,}]"
}

# create-or-update a client from a JSON representation
ensure_client() {
  local client_id="$1" file="$2" id
  id=$(client_uuid "$client_id")
  if [ -z "$id" ]; then
    kc create clients -r "$REALM" -f "$file" >/dev/null
    log "client $client_id created"
  else
    kc update "clients/$id" -r "$REALM" -f "$file"
    log "client $client_id updated"
  fi
}

ensure_audience_mapper() {
  local client_id="$1" id
  id=$(client_uuid "$client_id")
  if kc get "clients/$id/protocol-mappers/models" -r "$REALM" --fields name --format csv --noquotes \
       | tr -d '\r' | grep -qx "audience-$API_AUDIENCE"; then
    return
  fi
  kc create "clients/$id/protocol-mappers/models" -r "$REALM" \
    -s "name=audience-$API_AUDIENCE" -s protocol=openid-connect -s protocolMapper=oidc-audience-mapper \
    -s "config.\"included.custom.audience\"=$API_AUDIENCE" \
    -s 'config."access.token.claim"=true' -s 'config."id.token.claim"=false' >/dev/null
  log "audience mapper added to $client_id"
}

ensure_role() {
  kc get "roles/$1" -r "$REALM" >/dev/null 2>&1 \
    || { kc create roles -r "$REALM" -s "name=$1" -s "description=$2" >/dev/null; log "role $1 created"; }
}

# ensure_user username password email first last role
ensure_user() {
  local username="$1" password="$2" email="$3" first="$4" last="$5" role="$6" id
  id=$(kc get users -r "$REALM" -q "username=$username" -q exact=true --fields id --format csv --noquotes | head -n1 | tr -d '\r')
  if [ -z "$id" ]; then
    kc create users -r "$REALM" -s "username=$username" -s enabled=true -s "email=$email" \
      -s emailVerified=true -s "firstName=$first" -s "lastName=$last" >/dev/null
    log "user $username created"
  fi
  kc set-password -r "$REALM" --username "$username" --new-password "$password"
  kc add-roles -r "$REALM" --uusername "$username" --rolename "$role"
}

# ------------------------------------------------------------------ realm
if ! kc get "realms/$REALM" >/dev/null 2>&1; then
  kc create realms -s "realm=$REALM" -s enabled=true >/dev/null
  log "realm $REALM created"
fi
kc update "realms/$REALM" \
  -s displayName=Tiko -s registrationAllowed=true -s loginWithEmailAllowed=true \
  -s resetPasswordAllowed=true -s bruteForceProtected=true \
  -s accessTokenLifespan=300 -s ssoSessionIdleTimeout=1800 -s sslRequired=external

ensure_role client "Visitor taking tickets"
ensure_role agent  "Counter agent of an establishment"
ensure_role admin  "Establishment administrator"
# self-registered users are clients
kc add-roles -r "$REALM" --rname "default-roles-$REALM" --rolename client

# ------------------------------------------------------------------ clients
cat > "$TMP/frontend.json" <<EOF
{
  "clientId": "frontend",
  "name": "Tiko web",
  "enabled": true,
  "protocol": "openid-connect",
  "publicClient": true,
  "standardFlowEnabled": true,
  "implicitFlowEnabled": false,
  "directAccessGrantsEnabled": false,
  "serviceAccountsEnabled": false,
  "frontchannelLogout": true,
  "redirectUris": $(json_list "$FRONTEND_ORIGINS" "/*"),
  "webOrigins": $(json_list "$FRONTEND_ORIGINS" ""),
  "attributes": {
    "pkce.code.challenge.method": "S256",
    "post.logout.redirect.uris": "$POST_LOGOUT_REDIRECT_URIS/*"
  }
}
EOF
ensure_client frontend "$TMP/frontend.json"

cat > "$TMP/backend.json" <<EOF
{
  "clientId": "$BACKEND_CLIENT_ID",
  "name": "Tiko api-gateway",
  "enabled": true,
  "protocol": "openid-connect",
  "publicClient": false,
  "clientAuthenticatorType": "client-secret",
  "standardFlowEnabled": false,
  "implicitFlowEnabled": false,
  "directAccessGrantsEnabled": false,
  "serviceAccountsEnabled": true
}
EOF
ensure_client "$BACKEND_CLIENT_ID" "$TMP/backend.json"
if [ -n "$BACKEND_CLIENT_SECRET" ]; then
  BACKEND_UUID=$(client_uuid "$BACKEND_CLIENT_ID")
  kc update "clients/$BACKEND_UUID" -r "$REALM" -s "secret=$BACKEND_CLIENT_SECRET"
  log "configured the supplied secret for $BACKEND_CLIENT_ID"
fi

ensure_audience_mapper frontend
ensure_audience_mapper "$BACKEND_CLIENT_ID"

# ------------------------------------------------------------------ test users
ensure_user client "$TIKO_CLIENT_PASSWORD" client@tiko.test Sara   Benali client
ensure_user agent  "$TIKO_AGENT_PASSWORD"  agent@tiko.test  Mehdi  Karim  agent
ensure_user admin  "$TIKO_ADMIN_PASSWORD"  admin@tiko.test  Lina   Berrada admin
kc add-roles -r "$REALM" --uusername admin --rolename agent

# ------------------------------------------------------------------ secret hand-off
BACKEND_UUID=$(client_uuid "$BACKEND_CLIENT_ID")
SECRET=$(kc get "clients/$BACKEND_UUID/client-secret" -r "$REALM" \
  | sed -n 's/.*"value"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
[ -n "$SECRET" ] || { log "could not read the $BACKEND_CLIENT_ID secret"; exit 1; }
[ -z "$BACKEND_CLIENT_SECRET" ] || [ "$SECRET" = "$BACKEND_CLIENT_SECRET" ] \
  || { log "the configured $BACKEND_CLIENT_ID secret did not match"; exit 1; }

mkdir -p "$OUT_DIR"
umask 077
cat > "$OUT_DIR/gateway.properties.tmp" <<EOF
# written by keycloak/bootstrap.sh - do not edit, re-run keycloak-init instead
provider.client-id=$BACKEND_CLIENT_ID
provider.client-secret=$SECRET
EOF
mv "$OUT_DIR/gateway.properties.tmp" "$OUT_DIR/gateway.properties"
# readable by the gateway's uid only
chown "$SECRET_OWNER_UID:$SECRET_OWNER_UID" "$OUT_DIR/gateway.properties" 2>/dev/null || true
chmod 400 "$OUT_DIR/gateway.properties"
log "secret of $BACKEND_CLIENT_ID written to $OUT_DIR/gateway.properties"
log "done"
