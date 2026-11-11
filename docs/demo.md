# Tiko demo (7 minutes)

Commands below use PowerShell unless noted. Compose uses `http://localhost:3000`; kind Ingress
uses `http://tiko.local` and `http://keycloak.tiko.local`. The login, ticket, appointment, and
role-switch walkthrough is **validated statically only**; it was not completed in this check.

## Pre-demo checklist

- **Docker Engine:** `docker info --format '{{.ServerVersion}}'` → server version; checked here:
  `29.9.3`.
- **Free disk:** `Get-PSDrive -Name C | Select-Object Name,Free,Used` → enough space for
  images; checked here: about 82.5 GiB free.
- **kind cluster:** `kind get clusters` → `tiko`; present in this check.
- **Pods:** `kubectl get pods -n tiko` → application pods Ready; bootstrap Job Completed.
  Present in this check.
- **Ingress hosts:** `Select-String -Path "$env:SystemRoot\System32\drivers\etc\hosts" -Pattern 'tiko.local','keycloak.tiko.local'`
  → both names map to `127.0.0.1`; no matches in this check, so add the entries before using
  kind Ingress.
- **Compose credentials:** `Test-Path .env` → `True` after copying `.env.example` and replacing
  every placeholder. It was `False` in this checkout. Do not commit `.env`.

For a fresh Compose stack, copy/edit the local environment file, then start the stack:

```powershell
Copy-Item .env.example .env
notepad .env
docker compose up --build -d
docker compose ps
```

Set unique local passwords in `.env`; do not put credentials in the terminal transcript or
demo slides. The current check observed a frontend response, but its Compose Keycloak and
gateway probes returned an empty reply; no Compose login or booking is claimed as verified.

## Seven-minute script

### 0:00–0:45 — Architecture

**Say:** “A browser signs in with Keycloak, sends its token to one gateway, and the gateway routes to three business services and their separate databases.”

**Command:**

```powershell
Get-Content docs\architecture.md
```

**Expected:** The runtime and security diagrams show the browser, frontend, gateway, services,
three PostgreSQL databases, and Keycloak.

### 0:45–2:30 — Compose login, ticket, and appointment

**Say:** “The client signs in through Keycloak, takes a queue ticket, and books an appointment.”

**Command:**

```powershell
Start-Process http://localhost:3000/login
```

**Expected:** Keycloak login returns to Tiko. Use the local `client` test account configured
in `.env`; select a listed establishment and queue to take a ticket, then open
`http://localhost:3000/appointments` to book. A ticket code and a booked appointment should
appear. **Validated statically only; the interactive flow was not done.**

### 2:30–3:25 — Agent calls next; client is denied

**Say:** “Staff can advance the queue, while a client cannot call the next ticket.”

**Command:**

```powershell
Start-Process http://localhost:3000/tenant
```

**Expected:** Sign in as the `.env` `agent` user and use the counter’s call-next action; the
ticket advances. Then sign in as `client` and attempt the same action; expect HTTP 403. These
role-specific UI responses are **validated statically only**, not live-tested here.

### 3:25–3:50 — No token returns 401

**Say:** “The gateway rejects an API request that has no bearer token.”

**Command (terminal A):**

```powershell
kubectl port-forward -n tiko service/api-gateway 18888:8888
```

**Command (terminal B):**

```powershell
curl.exe -i --max-time 10 http://127.0.0.1:18888/api/establishments
```

**Expected:** HTTP `401`, `WWW-Authenticate: Bearer`, and `a valid bearer token is required`.
This was verified against the kind gateway. Stop the port-forward with Ctrl+C.

### 3:50–4:20 — Kubernetes resources

**Say:** “The demo stack runs in namespace `tiko`; only the app and identity provider have Ingress routes.”

**Command:**

```powershell
kubectl get pods,svc,ingress -n tiko
```

**Expected:** Ready frontend, gateway, queue, appointment, profile/notification, Keycloak, and
PostgreSQL pods; completed bootstrap Job; ClusterIP services; Ingress hosts
`tiko.local` and `keycloak.tiko.local`. This inventory was verified in the current cluster.

### 4:20–5:10 — NetworkPolicy allow and block

**Say:** “Calico allows a gateway-labelled source to reach a business service and blocks a source without that label.”

**Commands (run one at a time; each temporary pod is removed by `--rm`):**

```powershell
kubectl run np-allow --rm -i --restart=Never -n tiko --image=busybox:1.37 --image-pull-policy=IfNotPresent --labels=app=api-gateway --command -- sh -c 'wget -T 4 -q -O /dev/null http://appointment-service:8082/actuator/health && echo ALLOWED'
```

```powershell
kubectl run np-block --rm -i --restart=Never -n tiko --image=busybox:1.37 --image-pull-policy=IfNotPresent --labels=app=netprobe-blocked --command -- sh -c 'if wget -T 4 -q -O /dev/null http://appointment-service:8082/actuator/health; then echo UNEXPECTED_ALLOWED; exit 1; else echo BLOCKED; fi'
```

**Expected:** First prints `ALLOWED`; second reports a timeout and prints `BLOCKED`. Both
commands were run; both pods were deleted. This tests the policy’s label selector, not
application identity or authentication.

### 5:10–6:10 — GitHub Actions

**Say:** “Most CI checks are green, but this exact run is red because Trivy found frontend image vulnerabilities.”

**Commands:**

```powershell
gh run list --limit 5
gh pr checks 1
```

**Expected:** Latest PR run `42962403258` on `3fbe578` is **failure**. In the latest run,
backend/frontend builds, Gitleaks, Hadolint, filesystem scan, kubeconform, kind smoke, and
four other image scans passed; `Docker build and Trivy / frontend` failed with 11 HIGH and
1 CRITICAL findings. Do not call the run green.

### 6:10–7:00 — Limitations

**Say:** “This is a local demo baseline, not a production deployment: scaling, tenant isolation, egress controls, migrations, and operational tooling remain roadmap work.”

**Command:**

```powershell
Get-Content docs\architecture.md | Select-String -Pattern 'Known limitations and roadmap' -Context 0,10
```

**Expected:** The documented limitations match the source and manifests; npm audit and GitLab
pipeline status are explicitly marked not done.

## Do not claim

- Do not claim the latest GitHub Actions run is green.
- Do not claim Compose login, ticket/appointment creation, or the agent/client 200/403 path was
  demonstrated in this check.
- Do not claim HPA, metrics-server, tenant isolation, egress restrictions, Flyway, observability,
  Argo CD, Kyverno, k6, or production readiness.
- Do not report an npm advisory count: `npm` was unavailable and the audit was not run.
- Do not claim GitLab CI passed; no GitLab run was verified.

## Troubleshooting

- Docker engine unavailable: run `wsl --shutdown`, restart Docker Desktop, then retry
  `docker info`. Do **not** run `docker system prune` or any prune command.
- Ingress host does not resolve: confirm the hosts-file entries for both names and use an
  elevated editor to add `127.0.0.1 tiko.local keycloak.tiko.local`.
- Wrong or missing cluster: check `kubectl config current-context` and `kind get clusters`.
- Pods not Ready: inspect `kubectl get pods -n tiko` and `kubectl describe pod -n tiko <pod>`.
- Gateway login/API unavailable: check Keycloak and gateway pod readiness and confirm the
  browser-facing issuer matches the configured environment.

**Warning:** Do not run `kubectl apply -k k8s/` unchanged. `k8s/secret.yaml` contains
`REPLACE_WITH_*` placeholders. Create a valid `tiko-secrets` Secret first.
