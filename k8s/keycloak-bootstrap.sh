#!/usr/bin/env bash
# This is the Kubernetes ConfigMap copy of keycloak/bootstrap.sh.
set -euo pipefail

KC_URL="${KC_URL:-http://keycloak:8080}"
REALM="${REALM:-tiko}"
OUT_DIR="${OUT_DIR:-/bootstrap}"
FRONTEND_ORIGINS="${FRONTEND_ORIGINS:-http://tiko.local}"
POST_LOGOUT_REDIRECT_URIS="${FRONTEND_ORIGINS//,/\/\*##}"
API_AUDIENCE="${API_AUDIENCE:-tiko-api}"
BACKEND_CLIENT_ID="${BACKEND_CLIENT_ID:-tiko-backend}"
BACKEND_CLIENT_SECRET="${BACKEND_CLIENT_SECRET:?set PROVIDER_CLIENT_SECRET in tiko-secrets}"
SECRET_OWNER_UID="${SECRET_OWNER_UID:-10001}"

KCADM=/opt/keycloak/bin/kcadm.sh
KC_CONFIG=/tmp/kcadm.config
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

kc() { "$KCADM" "$@" --config "$KC_CONFIG"; }
log() { echo "[keycloak-bootstrap] $*"; }

for attempt in $(seq 1 60); do
  if "$KCADM" config credentials --config "$KC_CONFIG" --server "$KC_URL" --realm master \
       --user "$KC_ADMIN" --password "$KC_ADMIN_PASSWORD" >/dev/null 2>&1; then
    break
  fi
  [ "$attempt" = 60 ] && { log "Keycloak not reachable at $KC_URL"; exit 1; }
  sleep 3
done

client_uuid() {
  kc get clients -r "$REALM" -q "clientId=$1" --fields id --format csv --noquotes | head -n1 | tr -d '\r'
}

json_list() {
  local IFS=',' out="" item
  for item in $1; do out="$out\"$item$2\","; done
  echo "[${out%,}]"
}

ensure_client() {
  local client_id="$1" file="$2" id
  id=$(client_uuid "$client_id")
  if [ -z "$id" ]; then
    kc create clients -r "$REALM" -f "$file" >/dev/null
  else
    kc update "clients/$id" -r "$REALM" -f "$file"
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
}

ensure_role() {
  kc get "roles/$1" -r "$REALM" >/dev/null 2>&1 \
    || kc create roles -r "$REALM" -s "name=$1" -s "description=$2" >/dev/null
}

ensure_user() {
  local username="$1" password="$2" email="$3" first="$4" last="$5" role="$6" id
  id=$(kc get users -r "$REALM" -q "username=$username" -q exact=true --fields id --format csv --noquotes | head -n1 | tr -d '\r')
  if [ -z "$id" ]; then
    kc create users -r "$REALM" -s "username=$username" -s enabled=true -s "email=$email" \
      -s emailVerified=true -s "firstName=$first" -s "lastName=$last" >/dev/null
  fi
  kc set-password -r "$REALM" --username "$username" --new-password "$password"
  kc add-roles -r "$REALM" --uusername "$username" --rolename "$role"
}

if ! kc get "realms/$REALM" >/dev/null 2>&1; then
  kc create realms -s "realm=$REALM" -s enabled=true >/dev/null
fi
kc update "realms/$REALM" \
  -s displayName=Tiko -s registrationAllowed=true -s loginWithEmailAllowed=true \
  -s resetPasswordAllowed=true -s bruteForceProtected=true \
  -s accessTokenLifespan=300 -s ssoSessionIdleTimeout=1800 -s sslRequired=external

ensure_role client "Visitor taking tickets"
ensure_role agent "Counter agent of an establishment"
ensure_role admin "Establishment administrator"
kc add-roles -r "$REALM" --rname "default-roles-$REALM" --rolename client

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
BACKEND_UUID=$(client_uuid "$BACKEND_CLIENT_ID")
kc update "clients/$BACKEND_UUID" -r "$REALM" -s "secret=$BACKEND_CLIENT_SECRET"
ensure_audience_mapper frontend
ensure_audience_mapper "$BACKEND_CLIENT_ID"

ensure_user client "$TIKO_CLIENT_PASSWORD" client@tiko.test Sara Benali client
ensure_user agent "$TIKO_AGENT_PASSWORD" agent@tiko.test Mehdi Karim agent
ensure_user admin "$TIKO_ADMIN_PASSWORD" admin@tiko.test Lina Berrada admin
kc add-roles -r "$REALM" --uusername admin --rolename agent

SECRET=$(kc get "clients/$BACKEND_UUID/client-secret" -r "$REALM" \
  | sed -n 's/.*"value"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
[ "$SECRET" = "$BACKEND_CLIENT_SECRET" ] || { log "gateway client secret mismatch"; exit 1; }
mkdir -p "$OUT_DIR"
umask 077
printf '# Generated by the Keycloak bootstrap Job.\nprovider.client-id=%s\nprovider.client-secret=%s\n' \
  "$BACKEND_CLIENT_ID" "$SECRET" > "$OUT_DIR/gateway.properties"
chmod 400 "$OUT_DIR/gateway.properties"
log "realm configured; gateway secret verified"
