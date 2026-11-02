# Mogul on Cloud Foundry

Mogul is trialing Cloud Foundry on GKE as its next platform.
This file is what is specific to Mogul: Mogul's foundation, where its pieces live, and what is still open before Mogul moves onto it.

## The foundation

It was first built on 2026-11-01; `cf/lock/typical/` records what is running.

| | |
|---|---|
| CF API | `https://api.cf.applicationconcierge.com`, admin user `ccadmin` |
| GCP project | `joshlong` |
| Static IP | `applicationconcierge-cf-gateway`, `34.48.19.183`, regional, `us-east4` |
| Cluster | `applicationconcierge-cf`, GKE Standard, zonal `us-east4-a` |
| Node pools | 2 × `e2-standard-4` for the platform (`default-pool`), 1 × `n2d-standard-8` cell with a 200GB disk (`cells-n2d-standard-8-200g`) |
| Domain | `applicationconcierge.com`, registered at Gandi, which delegates `cf.` and `apps.` to the Cloud DNS zones `cf-applicationconcierge-com` and `apps-applicationconcierge-com` |
| Let's Encrypt account | `josh@joshlong.com` |
| Users | `ccadmin` and `josh` are admins; `dashaun` runs only the orgs he creates (self-service org creation is on, self-service sign-up is off) |

Mogul needs about 11GB and 7 cores with one processors instance, and 19GB and 15 cores with three.
One cell holds that in memory and disk even at peak, which is all Diego checks, but three processors instances would share its 8 cores, and one cell means a GKE node upgrade takes every app down until the node is back.
So before Mogul moves, raise `PLATFORM_NODES` to 3 and `CELL_NODES` to 2 in `cf/typical.env` and run `./up.sh --env typical.env cluster`, which applies it in a few minutes, without downtime.
Any other change of size or machine type is the same; `cf/README.md`, "Adding capacity", has what each change does and what it costs.
`CF_MAX_APP_DISK_MB=20480` and `CF_GRACEFUL_SHUTDOWN_SECONDS=300` are for processors: it expands audio to uncompressed WAV (10Gi on GKE), and JobRunr waits up to 4 minutes for running jobs on shutdown.

Mogul's production cluster is a different one: `joshlong`, GKE Autopilot, also in `us-east4` (context `gke_joshlong_us-east4_joshlong`).
`mogul.tools`, at Gandi, is the current production system, and is left alone until a cutover.

### Mogul's apps on it

The four applications run in the `mogul` org and `production` space, pushed by `pipeline` (`~/code/mogul/pipeline`, `bin/deploy.sh`, with `vars/typical.yml`) from production's images:

| App | Route |
|---|---|
| gateway | `https://studio.mogul.pub` |
| client | `https://ui.mogul.pub` |
| api | `https://api.mogul.pub` |
| processors | none |

They share production's backing services, so api and processors there are extra production replicas, and processors takes real jobs.
`mogul.pub` is a private domain of the `mogul` org, bound with `cf/handle_dns.sh -o mogul -s production -a gateway -host studio.mogul.pub`, which moved all of its DNS to the Cloud DNS zone `custom-mogul-pub` (its name servers at Gandi are `ns-cloud-b1` to `ns-cloud-b4.googledomains.com`).
Logging in through `studio.mogul.pub` works because its callback URLs are registered: `https://studio.mogul.pub/login/oauth2/code/auth0` in Auth0, and `https://studio.mogul.pub/oauth2/code/wordpress` in the WordPress.com app.

Before 2026-11-01, Mogul's apps ran on an earlier foundation, `mogul-cf` at `api.cf.mogul.pub` with `*.apps.mogul.pub` routes.
It was deleted with `./down.sh --purge` once they moved, and its configuration, lock files and Bitwarden items went with it.

### Bitwarden

| Item | Folder | Holds | Read by |
|---|---|---|---|
| `applicationconcierge-cf-installation-*` (14 secure notes) | `application_concierge` | the foundation's installer state | `cf/secrets-from-bitwarden.sh`, and `cf/up.sh` when the cluster doesn't have it |
| `application_concierge-cf-platform-installer-gandi-pat` | `application_concierge` | a Gandi token that can manage every domain of the account | `cf/handle_dns.sh` |
| `mogul-cf-image-puller-key` | `mogul` | the JSON key of `mogul-cf-image-puller@joshlong.iam.gserviceaccount.com`, which can only read `mogul-artifact-registry` | `pipeline/bin/deploy.sh`, as the image pull credential |

After `./up.sh` prints `new installer state`, run `./secrets-to-bitwarden.sh --env typical.env`.

### How Josh works with it

- He deletes `cf/temp/` after runs, on purpose, so the foundation's secrets don't stay on the laptop. Nothing is lost when he does.
- He pushes his own apps into the `test` org and space.
- He changes laptops at least once a year, so everything above lives in a repository or in Bitwarden, never only on a machine.
- His home router intermittently answers for some `applicationconcierge.com` names with a stale CNAME to `cfll-mailgun-inbound.cfapps.io`, so `cf` fails with "no such host" there. The records are right; `networksetup -setdnsservers Wi-Fi 1.1.1.1 8.8.8.8` works around it, and `networksetup -setdnsservers Wi-Fi Empty` undoes it.

## Before Mogul moves

All four of Mogul's applications run on the foundation as they are: each is one container with no sidecars, volumes or service account, configured entirely from environment variables, and every backing service is reached over the internet (Crunchy Bridge PostgreSQL, CloudAMQP, Redis Cloud, Elastic Cloud).
The disk cap, the graceful shutdown interval and private images are done.
These are still open, as of 2026-11-01:

1. **Autoscaling processors.** On GKE, KEDA scales it from the `jobrunr_jobs_by_module` view. Nothing equivalent is installed on the foundation. The options are CF's App Autoscaler with a custom metric, a small app that polls the view and calls the CF API to scale, or fixed instances.
2. **CPU and memory.** App pods get an `ephemeral-storage` request and limit, but no CPU or memory requests or limits. Diego places instances by memory and disk, and with `setCpuWeight: false` every instance gets equal CPU shares. Still to check: that the memory quota is enforced, and how two processors instances on one cell share it.
3. **Custom domains at cutover.** `studio.mogul.tools` and the others: `cf/handle_dns.sh -host studio.mogul.tools` delegates just that name from Gandi and gets its certificate, leaving the rest of `mogul.tools` where it is. Add its Auth0 and WordPress.com callback URLs first.
4. **Egress IPs.** If a backing service allows only certain source IPs, the foundation's nodes aren't on the list, and their IPs change as nodes come and go. That would need Cloud NAT with a static IP.
5. **`shareProcessNamespace: true`.** Every production pod sets it, CF has no equivalent, and the reason isn't recorded.
6. **The gateway doesn't exit on SIGTERM.** Its native image dies in Netty while shutting down: Lettuce's event loop threads throw `UnsupportedFeatureError: Support for Arena.ofShared is not active: enable with -H:+SharedArenaSupport` (Netty 4.2's Java 25 cleaner, which a native image only supports with that build flag), and the process never exits. On CF the cell kills it after `CF_GRACEFUL_SHUTDOWN_SECONDS` (5 minutes), on GKE the kubelet after 30 seconds. Nothing is lost, since a replacement is already serving, but every gateway stop takes that long. The likely fix is `<buildArg>-H:+SharedArenaSupport</buildArg>` for the gateway's `native-maven-plugin`. A macOS native build that had only served a health check exited cleanly, so check a fix on Linux, with real traffic: on CF, `cf restart gateway` and `cf logs gateway`.
