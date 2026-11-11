# Tiko on Kubernetes (Kustomize)

These manifests target a local Minikube or kind cluster with the NGINX Ingress controller and
a default `ReadWriteOnce` storage class. The public development hostnames are `tiko.local` for
the app and `keycloak.tiko.local` for Keycloak. The gateway's expected issuer is deliberately
the public URL `http://keycloak.tiko.local/realms/tiko`; the gateway reaches Keycloak internally
at `http://keycloak:8080`.

The checked-in `secret.yaml` contains placeholders only. Replace every
`REPLACE_WITH_*` value before applying it. For shared or production clusters, do not commit
credentials: create `tiko-secrets` from a secret manager or with `kubectl create secret` instead.
Set `PROVIDER_CLIENT_SECRET` to the gateway client secret you want Keycloak bootstrap to
configure. The bootstrap Job sets that exact value on the `tiko-backend` client; the gateway
reads the same value from the Kubernetes Secret. This explicit pre-provisioned Secret avoids
requiring the Keycloak container to run `kubectl` or grant the Job API permissions.

## Minikube

1. Start a cluster and enable ingress:

   ```sh
   minikube start --cpus=4 --memory=8192 --cni=calico
   minikube addons enable ingress
   ```

2. Edit `k8s/secret.yaml`; replace all placeholder values with unique local passwords and a
   gateway client secret. Confirm there are no `REPLACE_WITH_` values left:

   ```sh
   grep -n REPLACE_WITH_ k8s/secret.yaml
   ```

3. Build the images from the repository root and load them into Minikube. The frontend Vite
   config is baked into the image, so build it with the public Keycloak hostname:

   ```sh
   docker build -t tiko/queue-service:local ./backend/queue-service
   docker build -t tiko/appointment-service:local ./backend/appointment-service
   docker build -t tiko/profile-notification-service:local ./backend/profile-notification-service
   docker build -t tiko/api-gateway:local ./backend/api-gateway
   docker build --build-arg VITE_KEYCLOAK_URL=http://keycloak.tiko.local \
     --build-arg VITE_KEYCLOAK_REALM=tiko --build-arg VITE_KEYCLOAK_CLIENT_ID=frontend \
     -t tiko/frontend:local ./frontend
   minikube image load tiko/queue-service:local
   minikube image load tiko/appointment-service:local
   minikube image load tiko/profile-notification-service:local
   minikube image load tiko/api-gateway:local
   minikube image load tiko/frontend:local
   ```

4. Add local host mappings. Point both names at Minikube's IP (or use the ingress IP after
   `minikube tunnel`):

   ```sh
   minikube ip
   # Add "<minikube-ip> tiko.local keycloak.tiko.local" to your hosts file.
   ```

5. Apply the Kustomize bundle and wait for bootstrap and workloads:

   ```sh
   kubectl apply -k k8s/
   kubectl wait --for=condition=ready pod -l app=postgres -n tiko --timeout=5m
   kubectl wait --for=condition=ready pod -l app=keycloak -n tiko --timeout=5m
   kubectl wait --for=condition=complete job/keycloak-bootstrap -n tiko --timeout=5m
   kubectl wait --for=condition=available deployment --all -n tiko --timeout=8m
   kubectl get pods,services,ingress -n tiko
   ```

6. Browse http://tiko.local. The Keycloak console is at
   http://keycloak.tiko.local/admin; use `KC_ADMIN` and `KC_ADMIN_PASSWORD` in the Secret.

## kind

Create a cluster, install an NGINX Ingress controller compatible with your kind version, and
map `tiko.local` and `keycloak.tiko.local` to the host address used by the ingress controller.
Build the same five images above, then load them:

```sh
kind create cluster --name tiko
kind load docker-image --name tiko tiko/queue-service:local tiko/appointment-service:local \
  tiko/profile-notification-service:local tiko/api-gateway:local tiko/frontend:local
kubectl apply -k k8s/
kubectl wait --for=condition=complete job/keycloak-bootstrap -n tiko --timeout=5m
kubectl wait --for=condition=available deployment --all -n tiko --timeout=8m
```

Install an enforcing CNI (for example Calico) and an ingress controller using their upstream
kind-specific instructions before applying the Tiko bundle. kind's default kindnet CNI does not
enforce NetworkPolicies. The ingress controller is expected to run in namespace `ingress-nginx`,
which is the only external source allowed by the frontend and Keycloak ingress policies.

## Port-forward fallback

If ingress is unavailable, run these in separate terminals and map both public hostnames to
`127.0.0.1` in your hosts file:

```sh
kubectl port-forward -n tiko service/frontend 3000:8080
kubectl port-forward -n tiko service/keycloak 8080:8080
```

Then open http://tiko.local:3000. The frontend image must still be built with
`VITE_KEYCLOAK_URL=http://keycloak.tiko.local`; use a local proxy or hostname mapping if the
browser cannot resolve that host.

## Verification checklist

- `kubectl get pods -n tiko` shows the database, Keycloak, and all Deployments ready.
- `kubectl get job keycloak-bootstrap -n tiko` shows `1/1` complete.
- `kubectl logs job/keycloak-bootstrap -n tiko` reports the realm configured and gateway secret
  verified.
- `kubectl get ingress -n tiko` shows hosts `tiko.local` and `keycloak.tiko.local`.
- Open the app, sign in with the `client` test account, and verify queue, appointment, profile,
  and notification API requests pass through the JWT-protected gateway.
- Confirm business services and PostgreSQL have no Ingress and that NetworkPolicies are enforced
  by the selected CNI. An ingress controller does not itself enforce NetworkPolicies.

## Operational notes

- PostgreSQL is a single-replica StatefulSet with a PVC; use a suitable storage class and backup
  policy for any non-demo environment. Init scripts run only on an empty data directory.
- Keycloak uses its development-mode embedded store on a PVC. Replace it with a production
  Keycloak configuration and external database before production.
- The Secret manifest is a placeholder for a small local demo, not encrypted-at-rest secret
  management. Prefer an external secrets operator or cluster secret manager for real credentials.
- Services currently use Hibernate `ddl-auto=update`; adopt Flyway migrations before production.
- NetworkPolicies require a CNI that enforces them. Resource values and single replicas are demo
  defaults, not production capacity planning.
