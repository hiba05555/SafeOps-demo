# Tiko — MVP

```
browser ──► frontend (nginx :3000) ──/api──► api-gateway (:8888) ──► queue-service ──► postgres
   │                                             │ verifies JWT (JWKS, iss, aud=tiko-api)
   └──── Keycloak (:8080) ◄── PKCE login         └ forwards X-Auth-* identity headers
```

## Run

```bash
cp .env.example .env      # then change the passwords
docker compose up --build
```

Open http://localhost:3000. Test accounts are `client`, `agent`, and `admin`. Their passwords are the `TIKO_*_PASSWORD` values in `.env`. Self-registration is on, and new users get the `client` role.

| URL | What |
|---|---|
| http://localhost:3000 | app (client screens: `/search`, `/tickets`; counter: `/tenant` → Guichet, as `agent`) |
| http://localhost:8080/admin | Keycloak console (`KC_ADMIN` / `KC_ADMIN_PASSWORD`) |
| http://localhost:8888 | api-gateway (`/api/**`, `/auth/me`, `/actuator/health`) |

Start from scratch (wipes Keycloak and the database): `docker compose down -v`.

## Keycloak reproducibility

Nothing in Keycloak is configured by hand. `keycloak/bootstrap.sh` runs in the one-shot `keycloak-init` container on every `up` and converges Keycloak to:

- realm `tiko`, with realm roles `client` / `agent` / `admin` (`client` is the default)
- client `frontend`: public, Authorization Code + PKCE S256 only, redirect URIs for :3000 and :5173
- client `tiko-backend`: confidential, with a service account (the gateway's own client)
- an audience mapper that puts `tiko-api` in both clients' access tokens; the gateway rejects any other audience
- the test users

It then reads `tiko-backend`'s **generated** secret from Keycloak and writes it to the `gateway-secrets` volume (`gateway.properties`, mode 0400, owned by the gateway's uid). The gateway loads that file with `SPRING_CONFIG_IMPORT`. No secret is in git, in an image, or in `docker-compose.yml`. With `PROVIDER_SELF_TEST=true`, the gateway logs at startup whether that secret yields a valid token.

The script is idempotent, so you can re-run it any time with `docker compose run --rm keycloak-init`.

`KC_HOSTNAME` pins the issuer to `http://localhost:8080`, so tokens from the browser and from containers (which reach Keycloak as `keycloak:8080`) share one `iss`. The gateway sets `PROVIDER_JWT_ISSUER` to match.

## queue-service API (all behind the gateway, token required)

| Method | Path | Who |
|---|---|---|
| GET | `/api/establishments`, `/api/establishments/{id}` | any user |
| POST | `/api/tickets` `{queueId}` | any user (one active ticket per queue) |
| GET | `/api/tickets/me`, `/api/tickets/{id}` | owner |
| POST | `/api/tickets/{id}/cancel` | owner |
| GET | `/api/queues/{id}/counter` | agent / admin |
| POST | `/api/queues/{id}/call-next`, `/api/tickets/{id}/served`, `/api/tickets/{id}/no-show` | agent / admin |

The queue-service publishes no port. It trusts `X-Auth-*` only because the gateway is the only way in, and the gateway overwrites any `X-Auth-*` header a client sends.

## Local dev without Docker for the frontend

With the stack running, run `cd frontend && npm run dev` (http://localhost:5173). Vite proxies `/api` to the gateway on :8888.
