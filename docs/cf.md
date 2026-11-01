# Mogul on Cloud Foundry

Mogul is trialing Cloud Foundry on GKE as its next platform.
The platform itself is built by the `cf` repository (`~/code/mogul/cf`), which is generic: its `README.md` explains how it works.
This file is what is specific to Mogul: Mogul's foundation, where its pieces live, and what is still open before Mogul moves onto it.

## The foundation

Mogul's foundation is the default one in the `cf` repository: `cf/.env`.
`./up.sh`, with no `--env`, builds and maintains it.
It was first built on 2026-10-31; `cf/lock/default/` records what is running.

| | |
|---|---|
| CF API | `https://api.cf.mogul.pub`, admin user `ccadmin` |
| GCP project | `joshlong` |
| Static IP | `mogul-cf-gateway`, `8.228.96.33`, regional, `us-east4` |
| Cluster | `mogul-cf`, GKE Standard, zonal `us-east4-a` |
| Node pools | while trialing (`cf/.env`): 2 × `e2-standard-4` for the platform, 1 × `n2d-standard-8` cell with a 200GB disk. for production (`cf/production.env`): 3 platform nodes and 2 cells |
| Domain | `mogul.pub`, registered and hosted at Gandi (LiveDNS); `*.cf.mogul.pub` and `*.apps.mogul.pub` are A records to the static IP, TTL 300 |
| Let's Encrypt account | `josh@joshlong.com` |

Mogul needs about 11GB and 7 cores with one processors instance, and 19GB and 15 cores with three.
One cell holds that in memory and disk even at peak, which is all Diego checks, but three processors instances would share its 8 cores (12 on GKE today), and one cell means a GKE node upgrade takes every app down until the node is back.
So it runs at the trial size until Mogul moves, and then at production size: `./up.sh --env production.env cluster` (in `cf/`) applies it, in a few minutes, without downtime.
`CF_MAX_APP_DISK_MB=20480` and `CF_GRACEFUL_SHUTDOWN_SECONDS=300` are for processors: it expands audio to uncompressed WAV (10Gi on GKE), and JobRunr waits up to 4 minutes for running jobs on shutdown.

Mogul's production cluster is a different one: `joshlong`, GKE Autopilot, also in `us-east4` (context `gke_joshlong_us-east4_joshlong`).
`mogul.tools`, also at Gandi, is the current production system, and is left alone until a cutover.

### Bitwarden

Everything is in the `mogul` folder.

| Item | Holds | Read by |
|---|---|---|
| `mogul-cf-platform-installer-gandi-pat` | the Gandi Personal Access Token, in the `personal-access-token` field, meant to have only "Manage domain name technical configurations" on `mogul.pub` | `workspace/bin/env.java`, as `GANDI_PAT` |
| `mogul-cf-installation-*` (14 secure notes) | the foundation's installer state, saved with `cf/secrets-to-bitwarden.sh` on 2026-10-31 | `cf/secrets-from-bitwarden.sh`, and `cf/up.sh` when the cluster doesn't have it |

The first `./up.sh tls` on a new cluster needs the token:

```bash
bw-unlock && source ~/code/mogul/workspace/bin/env.sh
cd ~/code/mogul/cf && ./up.sh tls
```

### How Josh works with it

- He deletes `cf/temp/` after runs, on purpose, so the foundation's secrets don't stay on the laptop. Nothing is lost when he does.
- He pushes his own apps into the `test` org and space.
- He changes laptops at least once a year, so everything above lives in a repository or in Bitwarden, never only on a machine.

## Before Mogul moves

All four of Mogul's applications should run on the foundation as they are: each is one container with no sidecars, volumes or service account, configured entirely from environment variables, and every backing service is reached over the internet (Crunchy Bridge PostgreSQL, CloudAMQP, Redis Cloud, Elastic Cloud).
The disk cap and the graceful shutdown interval are already done.
These are still open, as of 2026-10-31:

1. **Autoscaling processors.** On GKE, KEDA scales it from the `jobrunr_jobs_by_module` view. Nothing equivalent is installed on the foundation. The options are CF's App Autoscaler with a custom metric, a small app that polls the view and calls the CF API to scale, or fixed instances.
2. **CPU and memory.** App pods get an `ephemeral-storage` request and limit, but no CPU or memory requests or limits. Diego places instances by memory and disk, and with `setCpuWeight: false` every instance gets equal CPU shares. Still to check: that the memory quota is enforced, and how two processors instances on one cell share it.
3. **Private images.** Mogul's images are in Artifact Registry (`us-docker.pkg.dev/joshlong/mogul-artifact-registry/<module>`), so `cf push --docker-image` needs `--docker-username _json_key` and a key in `CF_DOCKER_PASSWORD`. Still to check: that the cell agent passes those credentials through to the image pull.
4. **Custom domains at cutover.** `studio.mogul.tools` and the others need certificates on the foundation. `mogul.tools` is also at Gandi, so the same cert-manager setup works, given a Gandi token with access to `mogul.tools` (or HTTP-01 for each name).
5. **Egress IPs.** If a backing service allows only certain source IPs, the foundation's nodes aren't on the list, and their IPs change as nodes come and go. That would need Cloud NAT with a static IP.
6. **`shareProcessNamespace: true`.** Every production pod sets it, CF has no equivalent, and the reason isn't recorded.

The suggested next step is to push the real processors image with no route, its environment and a 10G disk, and run one real job.
