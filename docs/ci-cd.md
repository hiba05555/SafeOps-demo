# CI/CD pipelines

The repository includes both `.github/workflows/ci.yml` and `cd.yml` for GitHub Actions and
`.gitlab-ci.yml` for GitLab. Both verify the Java services and frontend, build every runtime
image, scan images with Trivy, validate rendered Kustomize manifests, and deploy a local kind
smoke environment. Deployments to a real cluster are gated by a manual production approval.

| Concern | GitHub Actions | GitLab CI |
|---|---|---|
| Pull requests / merge requests and feature branches | CI workflow runs Maven `verify`, frontend `npm ci` / typecheck / build, Docker builds and scans, kubeconform, and kind smoke. | `test`, `build`, and `scan` stages run for merge requests and branch pipelines; the kind smoke and manifests validation are part of `scan`. |
| Backend parallelism | Matrix: `api-gateway`, `queue-service`, `appointment-service`, `profile-notification-service`. | `parallel:matrix` over the same Maven modules. |
| Frontend | Node 22, npm cache, typecheck, production build. | Node 22, npm cache, typecheck, production build. |
| Container build and cache | Buildx builds the five runtime images without publishing on CI; GitHub Actions cache stores Maven, npm, and BuildKit layers. | Docker Buildx builds the same five images; Maven, npm, and local BuildKit caches are cached by ref/service. |
| Image scanning | Trivy scans locally built images for unfixed CRITICAL/HIGH OS and library vulnerabilities. | Trivy scans locally built images for unfixed CRITICAL/HIGH OS and library vulnerabilities. |
| Kubernetes verification | `kubectl kustomize` output is checked with kubeconform; a kind job builds/loads images, applies manifests with ephemeral test credentials, waits for bootstrap and Deployments. | Equivalent kubeconform and kind smoke jobs in the `scan` stage. |
| `develop` | After the CI workflow succeeds, CD publishes `dev-<sha>` image tags. | Build stage pushes `dev-<sha>` to the GitLab Container Registry. |
| `main` | After the CI workflow succeeds, CD publishes `<sha>` and `latest`, then waits for approval in the GitHub `production` Environment before rollout. | Build stage pushes `<sha>` and `latest`; the `deploy-production` job is manual and uses the protected `production` environment. |
| Manual publishing/deployment | `workflow_dispatch` builds/publishes the selected ref; production deployment runs only when dispatched on `main`. | Pushes to `main` expose the manual production deployment job. |

## Required GitHub settings

- Enable package write permission for the Actions `GITHUB_TOKEN` at repository/org level.
- Create the `production` Environment and configure required reviewers to enforce the approval
  gate. Configure `KUBECONFIG_B64` (base64-encoded kubeconfig), `GHCR_PULL_USERNAME`, and
  `GHCR_PULL_TOKEN` as Environment secrets. The pull token needs `read:packages`.
- Set the `VITE_KEYCLOAK_URL` repository variable to the browser-visible Keycloak URL used by
  the target environment. The default is for local development and is not suitable for a
  production frontend build.
- Provision namespace `tiko`, its non-placeholder `tiko-secrets` Secret, ingress, storage,
  database, and service configuration before enabling production image rollout. The workflow
  updates deployment image tags; it does not provision cluster infrastructure or application
  credentials.

## Required GitLab settings

- Use a Docker-capable runner that permits Docker-in-Docker (privileged service support).
- Configure masked/protected variables `KUBECONFIG` (File type), `REGISTRY_PULL_USER`, and
  `REGISTRY_PULL_TOKEN` with read access to the project Container Registry.
- Set the `VITE_KEYCLOAK_URL` CI/CD variable to the browser-visible Keycloak URL for the
  target environment. The default local URL is not suitable for a production frontend build.
- Protect the `production` environment/branch and configure the required approval rules in
  GitLab. The deployment job is manual and runs only on `main`.
- Provision the cluster and its non-placeholder `tiko-secrets` Secret before deploying. The
  job updates images only.
- Build images bake the Vite Keycloak URL into frontend assets. Configure the GitHub variable or
  GitLab CI/CD variable before publishing deployable images.

Deploy jobs replace the pod `imagePullSecrets` value with a namespaced `registry-pull` Secret and
roll the five deployments to the SHA-tagged registry images. Keep registry pull credentials
read-only and scoped to the target project/package.
