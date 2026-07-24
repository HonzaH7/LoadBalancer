# DevOps Walkthrough — from code to a live cloud app

This document explains how this Java load balancer goes from source code to a
running application on a public IP in the cloud, and the concepts behind each
step. It doubles as a study/reference guide.

## The big picture

```
Your code (Java LB + backends)
   -> Docker image            (multi-stage build)
   -> GitHub Actions CI       (test -> build -> publish)
   -> GHCR                    (the image registry / "warehouse")
   -> Terraform               (provisions a Kubernetes cluster in the cloud)
   -> kubectl + manifests     (deploys Deployments + Services)
   -> DigitalOcean LoadBalancer (a public IP)
   -> live on the internet, load-balanced across pods
```

Every layer is described **as code** and lives in git. This is *Infrastructure
as Code (IaC)*: nothing is clicked together by hand, so the whole environment is
versioned, reviewable, and reproducible.

---

## Stage 1 — Docker

**Goal:** package the app so it runs anywhere, as separate processes.

- `Dockerfile` — a **multi-stage build**: stage 1 uses a Maven+JDK image to
  compile a `.jar`; stage 2 copies only that jar into a slim JRE image. The
  final image contains just Java + the app, not the build tools.
- **One image, two roles:** `ENTRYPOINT` is `java -cp app.jar`, `CMD` is the
  default class (`LbMain`). Override the command with `BackendMain` and the same
  image runs a backend instead.
- `docker-compose.yml` — wires one load balancer in front of three backends on
  an internal network. Services find each other by **service name (DNS)**, which
  is why the load balancer's backend list is configured, not hard-coded.

**Key idea:** a container runs *one process*. To containerize a monolith you
split it into separate processes, each configured through **environment
variables** (a 12-factor principle): `PORT`, `BACKENDS`.

Run it locally:
```
docker compose up --build
# then visit http://localhost:8080/
```

---

## Stage 2 — CI/CD with GitHub Actions

**Goal:** on every push, automatically test the code, build the image, and
publish it.

- `.github/workflows/ci.yml` defines the pipeline. GitHub runs it on its own
  servers whenever the trigger fires.
- **Trigger:** `on: push` / `pull_request` to `main`.
- **Job `test`:** checks out the code, installs JDK 21, runs `mvn test`.
- **Job `docker`:** builds the image and, on push to `main`, publishes it to
  **GHCR** (GitHub Container Registry) tagged with both `latest` and the commit
  SHA. `needs: test` gates it — the image is only built/published if tests pass.
- **Auth without stored secrets:** the workflow uses the automatic
  `GITHUB_TOKEN` (created per run) plus `permissions: packages: write`. Nothing
  is stored by hand.

**Key ideas:**
- CI is just a robot running the same commands you'd run by hand, on a fresh
  machine, every time you push.
- **Tag strategy:** `latest` (moving pointer) + commit SHA (immutable, for
  rollback and traceability).
- Don't publish images from pull requests — only from `main`
  (`if: github.event_name == 'push'`).

---

## Stage 3 — Kubernetes (locally on kind)

**Goal:** run the app on a cluster that keeps it alive and load-balances it.

`kind` = *Kubernetes IN Docker*: it runs a whole cluster where each node is a
Docker container on your machine. Free, local, disposable.

- **Deployment** — declares a desired state ("keep 3 backend pods running"). If
  a pod dies, the controller creates a replacement. This is the
  **reconciliation loop**: it constantly compares desired vs actual and fixes
  the difference. That's the heart of Kubernetes and how it self-heals.
- **Service** — a stable address in front of a set of pods that
  **load-balances** across them. It is, in effect, a built-in load balancer.
- **Labels** are the glue: Deployments stamp pods with `app: backend`, and the
  Service targets `app: backend`. Nothing references pods by name or IP.

Manifests: `k8s/backend.yaml`, `k8s/loadbalancer.yaml`.

**The "aha" moment:** the load balancer's `BACKENDS` pointed at *one* address —
the `backend` Service — yet requests spread across all three pods. The
**Kubernetes Service did the load balancing**, not the custom Java code. In a
real cluster you'd expose a Service (or an Ingress) and let Kubernetes balance;
a hand-written L4 load balancer is exactly what a Service already is. Having
built one, you understand precisely what the Service does under the hood.

```
kind create cluster --name loadbalancer
kind load docker-image loadbalancer:local --name loadbalancer   # sideload the local image
kubectl apply -f k8s/backend.yaml
kubectl apply -f k8s/loadbalancer.yaml
```

---

## Stage 4 — Real cloud (DigitalOcean + Terraform)

**Goal:** the same app, on a managed cluster, on a public IP.

- **Terraform** (`terraform/main.tf`) provisions the DigitalOcean Kubernetes
  cluster *as code*. Same "describe the desired state" idea as Kubernetes, but
  for the infrastructure itself. The API token is passed via the
  `DIGITALOCEAN_TOKEN` environment variable — **never** written in a file or
  committed.
- **Cloud manifests** (`k8s/cloud/`) differ from local in two ways:
  1. `image: ghcr.io/honzah7/loadbalancer:latest` — the cluster pulls from GHCR
     (the package is public).
  2. The load balancer Service is `type: LoadBalancer`, so DigitalOcean
     provisions a real cloud load balancer with a **public IP**.

```
# provision the cluster
cd terraform
export DIGITALOCEAN_TOKEN=...        # PowerShell: $env:DIGITALOCEAN_TOKEN = "..."
terraform init
terraform plan
terraform apply

# deploy the app
kubectl --kubeconfig terraform/kubeconfig.yaml apply -f k8s/cloud/

# find the public IP
kubectl --kubeconfig terraform/kubeconfig.yaml get svc loadbalancer   # EXTERNAL-IP
```

**Tear down (to stop billing) — order matters:**
```
kubectl --kubeconfig terraform/kubeconfig.yaml delete -f k8s/cloud/   # removes the cloud LB
terraform destroy                                                     # removes the cluster
```
Delete the `LoadBalancer` Service *first*: it was created by Kubernetes, not by
Terraform, so `terraform destroy` alone would leave an orphaned (billing) load
balancer.

---

## Interview talking points

- **IaC:** "Everything — the image, the CI pipeline, the Kubernetes manifests,
  and the cluster itself — is code in git. The environment is reproducible."
- **Immutable artifacts:** "CI builds one image, tagged with the commit SHA, and
  the same image runs in every environment."
- **Reconciliation loop:** "A Deployment declares a desired state; the controller
  continuously reconciles actual to desired, which is how Kubernetes self-heals."
- **Service = load balancer:** "A Kubernetes Service is an L4 load balancer with
  health-aware endpoint selection — I know because I wrote the same thing in
  Java before I saw the Service do it for me."
- **TLS termination at the edge:** "In production, HTTPS is terminated at an
  Ingress (e.g. nginx-ingress) with certificates from cert-manager/Let's Encrypt;
  traffic inside the cluster stays plain HTTP."

## War stories (real bugs fixed)

- **RST-truncated responses:** closing a TCP socket that still has unread inbound
  data sends an RST instead of a clean FIN, which intermittently truncated
  responses. Fixed by reading the request before responding.
- **Proxy deadlock:** once backends correctly waited for a request, the load
  balancer had to actually forward it — the original code never did. Making it a
  real reverse proxy fixed the hang.
- **Leaked secret:** a DO API token was pasted into a screenshot; the right
  response is to revoke and regenerate it. Secrets go in environment variables,
  never in files or images.

## How to extend it (HTTPS)

1. Point a domain name at the load balancer IP (certificates are issued for
   domains, not bare IPs).
2. Add an **Ingress controller** (nginx-ingress).
3. Add **cert-manager** + **Let's Encrypt** for automatic, free, auto-renewing
   certificates.
4. The Ingress terminates TLS; backends keep serving plain HTTP internally.
