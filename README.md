# Tiko

Tiko is a local development stack for queues, appointments, and profile notifications. All
business APIs are authenticated at the gateway; backend services accept the gateway's verified
`X-Auth-*` headers and are not published to the host in the default configuration.

```text
                         ┌────────────── edge network ────────────────┐
browser ──► frontend :3000 ──/api──► api-gateway :8888                │
   │                                    │                             │
   │                                    ├── queue-service :8081 ─────┐│
   │                                    ├── appointment-service :8082┤│
   │                                    └── profile-notification :8083
   │                                                                  │
   └──────────────► Keycloak :8080 ◄─────────────────────────────────┘
                                        backend network
                                                   │
                                           PostgreSQL :5432
                                      ┌────────────┼─────────────┐
                                      tiko     appointments    profiles
```

The browser uses Keycloak Authorization Code + PKCE. The gateway validates tokens and forwards
the identity headers to backend services. The shared PostgreSQL container creates separate
databases and login roles for the queue, appointment, and profile-notification services.

## Run locally

1. Copy `.env.example` to `.env` and replace every `replace-with-...` value with a unique
   development credential.
2. Start the stack:

   ```sh
   docker compose up --build
   ```

3. Open http://localhost:3000. The Keycloak console is at http://localhost:8080/admin; its
   credentials are `KC_ADMIN` and `KC_ADMIN_PASSWORD` from `.env`. Test accounts are `client`,
   `agent`, and `admin`; their passwords are the corresponding `TIKO_*_PASSWORD` values.

Only the frontend, gateway, and Keycloak are published by default, bound to loopback. PostgreSQL
and business-service ports are internal to the backend network. To publish those ports for local
debugging, use the additional override file:

```sh
docker compose -f docker-compose.yml -f docker-compose.debug.yml up --build
```

The debug ports are loopback-only and configured by the `*_DEBUG_PORT` variables in `.env`.
`docker compose down` stops the stack. `docker compose down -v` also deletes Keycloak, PostgreSQL,
and gateway-secret volumes.

PostgreSQL's `db/init.sh` runs only when the data volume is initialized for the first time. If
database names, users, or passwords change later, update the existing database roles manually or
remove the PostgreSQL volume and recreate it; removing that volume deletes all service data.

## API

All API paths below are served by the gateway and require a valid bearer token. Client routes are
owner-scoped; staff-only routes require the `agent` or `admin` realm role.

| Method | Path | Access |
|---|---|---|
| GET | `/api/establishments`, `/api/establishments/{id}` | Any authenticated user |
| POST | `/api/tickets` | Any authenticated user |
| GET | `/api/tickets/me`, `/api/tickets/{id}` | Ticket owner |
| POST | `/api/tickets/{id}/cancel` | Ticket owner |
| GET | `/api/queues/{id}/counter` | Agent / admin |
| POST | `/api/queues/{id}/call-next` | Agent / admin |
| POST | `/api/tickets/{id}/served`, `/api/tickets/{id}/no-show` | Agent / admin |
| POST | `/api/appointments` | Any authenticated user |
| GET | `/api/appointments/me`, `/api/appointments/{id}` | Appointment owner |
| POST | `/api/appointments/{id}/cancel` | Appointment owner |
| GET | `/api/appointments?establishmentId=...` | Agent / admin |
| POST | `/api/appointments/{id}/complete`, `/api/appointments/{id}/no-show` | Agent / admin |
| GET / PUT | `/api/profile/preferences` | Current user |
| GET | `/api/notifications` | Current user |
| POST | `/api/notifications/{id}/read` | Notification owner |
| POST | `/api/notifications` | Agent / admin |

The queue, appointment, and profile-notification services use separate PostgreSQL databases.
They currently use Hibernate `ddl-auto=update` for the MVP; use Flyway-managed migrations before
production. Notification creation stores notifications; delivery through SMS or email is not
implemented by this API.

## Keycloak setup

`keycloak/bootstrap.sh` runs in the one-shot `keycloak-init` container after Keycloak is healthy.
It converges the `tiko` realm, clients, roles, test users, and audience mapper, then writes the
generated gateway client secret to the `gateway-secrets` volume. The secret is not stored in the
repository or Compose file. `PROVIDER_SELF_TEST=true` makes the gateway log whether its
client-credentials token check succeeds.

The bootstrap script is safe to rerun:

```sh
docker compose run --rm keycloak-init
```

Self-registration is enabled; newly registered users receive the `client` role.

## Frontend development server

With the stack running, start Vite from the frontend directory:

```sh
cd frontend
npm run dev
```

Open http://localhost:5173. Vite proxies `/api` requests to the gateway on port 8888.
