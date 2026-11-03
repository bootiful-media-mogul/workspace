# Mogul on Cloud Foundry

Mogul runs on the mypaas foundation, open source Cloud Foundry on BOSH in the GCP project `mypaas-cf`, since 2026-11-03 (see "Moving to mypaas" below).
Before that it ran on Cloud Foundry on GKE (the typical foundation, below), from 2026-11-02.
This file is what is specific to Mogul: Mogul's foundation, where its pieces live, and what is still open.

## Mypaas, the foundation since 2026-11-03

It is built by `~/code/application-concierge/bosh-platform-installer` (`mypaas.env`); its README is the full record.

| | |
|---|---|
| CF API | `https://api.cf.mypaas.online`; `./up.sh --env mypaas.env admin` prints admin's password |
| GCP project | `mypaas-cf`, its unit of isolation: `./down.sh` deletes all of it |
| Load balancer | `34.117.196.24`, Google's global HTTPS load balancer, with a Certificate Manager map of every domain's certificate |
| Cells | 1 × `n2d-standard-8`, 200GB (`CELL_*` in `mypaas.env`) |
| App settings | `CF_MAX_APP_DISK_MB=20480`, `CF_GRACEFUL_SHUTDOWN_SECONDS=300`, Docker images allowed, as on the typical foundation |
| Deploys | `pipeline`, with `vars/mypaas.yml`, as the user `mogul-deployer`, a space developer of `mogul/production` only; its password is in the foundation's CredHub (`/mypaas/mogul/deployer_password`) and the pipeline repository's `CF_PASSWORD` secret |
| DNS | `mogul.tools` is at Gandi LiveDNS, a private domain of the `mogul` org; `app-domain.sh` sets up an app's domain, certificate and records |

The apps and routes are the table under "Mogul's apps on it": all on `mogul.tools`, and nothing on `mogul.pub`, which Mogul no longer uses anywhere (step 6 below).
The marketplace has Cloud SQL PostgreSQL and MySQL and Cloud Storage buckets, though Mogul uses none of them: its backing services are the same Crunchy Bridge, CloudAMQP, Redis Cloud and Elastic Cloud.

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
Raising `PLATFORM_NODES` to 3 and `CELL_NODES` to 2 in `cf/typical.env` and running `./up.sh --env typical.env cluster` fixes both, in a few minutes and without downtime; it hasn't been done yet.
Any other change of size or machine type is the same; `cf/README.md`, "Adding capacity", has what each change does and what it costs.
`CF_MAX_APP_DISK_MB=20480` and `CF_GRACEFUL_SHUTDOWN_SECONDS=300` are for processors: it expands audio to uncompressed WAV (10Gi on GKE), and JobRunr waits up to 4 minutes for running jobs on shutdown.

### Capacity, 2026-11-02

Every app runs on the one cell, and the platform nodes run only CF itself.
Eleven apps in four orgs (`mogul`, `developer-advocacy`, `bootiful-podcast`, `test`) ask for 12.3GB of memory and 41GB of disk, Mogul's five 9.5GB of that.
The cell has about 27.7GiB of memory and 97GiB of disk to give pods; with the platform's own pods on it, about 14GiB and 60GiB are left, enough for three processors instances or a dozen small apps.
Actual use is far lower (0.2 cores and 3.9GiB while idle), so CPU, which every app shares, is what runs out first under load, not memory.
The org quota (100GB) is not the limit.
`kubectl describe node` on the cell (`Allocated resources`) and `cf curl /v3/processes` give these numbers again.

At list prices the foundation is about $510 a month: about $220 for the two platform nodes, $220 for the cell, $46 for disks and $22 for the load balancer and static IP; the zonal cluster's fee is under GKE's free tier.
That is about what Mogul and the sites cost on GKE Autopilot, which charged for each pod's requests at roughly 2.5 times a VM's price per core.
A second cell adds about $220; more apps cost nothing until the cell is full.

### Mogul's apps on it

The five applications run in the `mogul` org and `production` space, pushed by `pipeline` (`~/code/mogul/pipeline`, `bin/deploy.sh`, with `vars/typical.yml`) from production's images:

| App | Route |
|---|---|
| gateway | `https://studio.mogul.tools` |
| client | `https://ui.mogul.tools` |
| api | `https://api.mogul.tools` |
| processors | none |
| redirect | `https://mogul.tools`, a 302 to `https://studio.mogul.tools` with the path kept (`pipeline/redirect/`) |

`mogul.tools` is a private domain of the `mogul` org, and all of its DNS is in the Cloud DNS zone `custom-mogul-tools` (its name servers at Gandi are `ns-cloud-a1` to `ns-cloud-a4.googledomains.com`); "Moving mogul.tools" below is how it got there.
Login through `studio.mogul.tools` needed nothing new: production on GKE had already registered `https://studio.mogul.tools/login/oauth2/code/auth0` in Auth0 and `https://studio.mogul.tools/oauth2/code/wordpress` in the WordPress.com app.

The apps still answer on `mogul.pub` too, a private domain of the `mogul` org, bound with `cf/handle_dns.sh -o mogul -s production -a gateway -host studio.mogul.pub`, which moved all of its DNS to the Cloud DNS zone `custom-mogul-pub` (its name servers at Gandi are `ns-cloud-b1` to `ns-cloud-b4.googledomains.com`).

Before 2026-11-01, Mogul's apps ran on an earlier foundation, `mogul-cf` at `api.cf.mogul.pub` with `*.apps.mogul.pub` routes.
It was deleted with `./down.sh --purge` once they moved, and its configuration, lock files and Bitwarden items went with it.

### Bitwarden

| Item | Folder | Holds | Read by |
|---|---|---|---|
| `applicationconcierge-cf-installation-*` (14 secure notes) | `application_concierge` | the foundation's installer state | `cf/secrets-from-bitwarden.sh`, and `cf/up.sh` when the cluster doesn't have it |
| `application_concierge-cf-platform-installer-gandi-pat` | `application_concierge` | a Gandi token that can manage every domain of the account | `cf/handle_dns.sh` |
| `mogul-cf-image-puller-key` | `mogul` | the JSON key of `mogul-cf-image-puller@joshlong.iam.gserviceaccount.com`, which can only read `mogul-artifact-registry` | `pipeline/bin/deploy.sh`, as the image pull credential |
| `mogul-production-secrets` | `mogul` | production's secrets, one hidden field per variable, copied from `mogul-secrets` on GKE by `pipeline/bin/secrets-to-bitwarden.sh` | `pipeline/bin/env-from-bitwarden.sh` |

After `./up.sh` prints `new installer state`, run `./secrets-to-bitwarden.sh --env typical.env`.

### How Josh works with it

- He deletes `cf/temp/` after runs, on purpose, so the foundation's secrets don't stay on the laptop. Nothing is lost when he does.
- He pushes his own apps into the `test` org and space.
- He changes laptops at least once a year, so everything above lives in a repository or in Bitwarden, never only on a machine.
- His home router intermittently answers for some `applicationconcierge.com` names with a stale CNAME to `cfll-mailgun-inbound.cfapps.io`, so `cf` fails with "no such host" there. The records are right; `networksetup -setdnsservers Wi-Fi 1.1.1.1 8.8.8.8` works around it, and `networksetup -setdnsservers Wi-Fi Empty` undoes it.

## Moving to mypaas, 2026-11-03

1. The foundation got the typical foundation's disk cap, graceful shutdown interval and Docker images, the `mogul` org and `production` space, `mogul.tools` and `mogul.pub` as private domains of `mogul`, and the user `mogul-deployer`.
2. `pipeline` got `vars/mypaas.yml` (with the foundation's API, `cf-api`), and its workflow deploys with it; `CF_USERNAME` and `CF_PASSWORD`, as repository secrets, override the organization's, which are still the typical foundation's. Its first run deployed all five apps, with no DNS pointing at them yet.
3. Every app was checked through mypaas's load balancer (`curl --resolve`), and its logs for Postgres and RabbitMQ connections: the backing services don't limit source IPs, so the new foundation's egress addresses needed nothing.
4. `bosh-platform-installer/domain.sh mogul.tools` issued the certificate, checked each routed host through the load balancer over it, and pointed `mogul.tools`, `studio`, `ui`, `api` and `*` at it, at Gandi; `app-domain.sh` did the same for `studio.mogul.pub` and `api.mogul.pub`.
5. Once the old records' 300-second TTL had passed, Mogul's five apps were stopped on the typical foundation, and later the same day deleted from it, with the `mogul.tools` and `mogul.pub` domains, so its custom domains loop stops recreating their Cloud DNS zones. There is no way back to the typical foundation.

6. `mogul.pub` was then dropped altogether: its private domain (with the `studio` and `api` routes) from mypaas, its certificate and map entries, every record of its Gandi LiveDNS zone, and its Cloud DNS zone `custom-mogul-pub` in `joshlong`. The domain stays registered at Gandi, with an empty zone; what the zone had is in `bosh-platform-installer/temp/to-gandi/mogul.pub.gandi-before-removal.json`.

Still to do: `bosh-platform-installer/to-gandi.sh --delete-zones`, from 2026-11-05, deletes the zone `custom-mogul-tools` in `joshlong`.

## Still open

Mogul's applications run on the foundation as they did on GKE: each is one container with no sidecars, volumes or service account, configured entirely from environment variables, and every backing service is reached over the internet (Crunchy Bridge PostgreSQL, CloudAMQP, Redis Cloud, Elastic Cloud).
The disk cap, the graceful shutdown interval and private images are done.
These are still open, as of 2026-11-03:

1. **Autoscaling processors.** On GKE, KEDA scaled it from the `jobrunr_jobs_by_module` view; processors now runs as one fixed instance. Nothing equivalent is installed on the foundation. The options are CF's App Autoscaler with a custom metric, a small app that polls the view and calls the CF API to scale, or fixed instances.
2. **CPU and memory.** App pods get an `ephemeral-storage` request and limit, but no CPU or memory requests or limits. Diego places instances by memory and disk, and with `setCpuWeight: false` every instance gets equal CPU shares. Still to check: that the memory quota is enforced, and how two processors instances on one cell share it.
3. **Moving `mogul.tools`.** Done on 2026-11-02; see "Moving mogul.tools" below.
4. **Egress IPs.** None of the backing services limits source IPs: the move to mypaas, whose egress addresses differ, needed nothing. If one ever does, the foundation would need Cloud NAT with a static IP.
5. **`shareProcessNamespace: true`.** Every production pod sets it, CF has no equivalent, and the reason isn't recorded.
6. **The gateway didn't exit on SIGTERM.** Fixed on 2026-11-03, in the gateway's 98f8e67.
   Once it had served real traffic, its native image threw `UnsupportedFeatureError: Support for Arena.ofShared is not active` on Lettuce's event loop while shutting down (Netty 4.2's Java 25 cleaner), and hung until the cell killed it after `CF_GRACEFUL_SHUTDOWN_SECONDS` (5 minutes).
   The `native-maven-plugin` now builds with `-H:+UnlockExperimentalVMOptions -H:+SharedArenaSupport`, and a stopped instance exits in under a second.
   To reproduce locally, the binary has to proxy some requests before it gets SIGTERM: one that only served a health check exits cleanly either way.

## Moving mogul.tools

`mogul.tools` is registered at Gandi and served by Gandi LiveDNS.
As of 2026-11-01 it holds the apex (Gandi's parking page, `217.70.184.38`) and three A records, each to a GKE Ingress's own static IP: `studio` (`mogul-gateway-ip`, `34.149.94.25`), `ui` (`mogul-client-ip`, `34.49.42.111`) and `api` (`mogul-api-ip`, `136.81.13.160`), with Google-managed certificates.
It has no MX, TXT or CAA records, and isn't signed with DNSSEC.

The move takes all of the domain's DNS to Cloud DNS first, with those three records still pointing at GKE, so nothing changes for users until the records are flipped, one at a time, after CF has its certificate.

1. In the `cf` repository, with Bitwarden unlocked (for the Gandi token) and `eval "$(./up.sh --env typical.env shell)"`:

   ```bash
   ./handle_dns.sh --env typical.env -o mogul -s production -a gateway -host mogul.tools --dry-run
   ./handle_dns.sh --env typical.env -o mogul -s production -a gateway -host mogul.tools
   ```

   The dry run should list the `studio`, `ui` and `api` A records to copy and the apex A record to replace.
   The real run makes `mogul.tools` a private domain of `mogul`, maps the apex to the gateway, copies the three records into the zone `custom-mogul-tools` (where they override its `*.mogul.tools` record to the foundation's gateway), switches the name servers at Gandi, and waits for the `mogul.tools` and `*.mogul.tools` certificate.
   Only the apex changes for users: the parking page goes.
2. Deploy `pipeline` with `vars/typical.yml` on `mogul.tools`, which adds the `studio`, `ui` and `api` routes on `mogul.tools`, and points the apps at each other there.
   The domain has to exist in CF first, or `cf push` fails on the routes.
   Until step 4, `MOGUL_SERVICE_HOST` and `MOGUL_CLIENT_HOST` still reach GKE's api and client, which is harmless: they are the same images on the same backing services.
3. Check each host through the foundation's gateway, before any user gets there:

   ```bash
   for h in studio ui api; do curl -s -o /dev/null -w "$h %{http_code}\n" --resolve $h.mogul.tools:443:34.48.19.183 https://$h.mogul.tools/; done
   ```

4. Flip a host by deleting its A record from `custom-mogul-tools`, which leaves `*.mogul.tools` to answer:

   ```bash
   gcloud --project joshlong dns record-sets delete studio.mogul.tools. --type=A --zone=custom-mogul-tools
   ```

   The records have a TTL of 300 seconds, so a host moves within about five minutes, and putting its record back (`gcloud dns record-sets create ... --rrdatas=<the GKE IP>`) is the way back.
5. Leave GKE's Mogul running for at least a day after the last flip: resolvers that cached Gandi's name servers before the switch may keep asking Gandi, whose zone still has the GKE addresses, for up to Gandi's NS TTL.
   Then the `mogul.pub` routes can be unmapped (`cf unmap-route`) and its private domain deleted; its zone stays, per `cf/AGENTS.md`.

### How it went, 2026-11-02

Steps 1 to 4 are done: `studio`, `ui` and `api.mogul.tools` were moved at 07:41 UTC, and public resolvers had the new address at once.
The first certificate order failed, because it went out 36 seconds after the switch at Gandi, before the `.tools` servers referred to the new zone, and cert-manager backed off for an hour; it was retried by hand, and `cf/domains/reconcile.sh` now waits for the zone above (`cf/README.md`, "Custom domains").

Mogul on GKE was switched off right away rather than after a day (step 5), since Josh is its only user, and the `joshlong` cluster (GKE Autopilot, `us-east4`) was deleted the same day.
There is no way back to GKE; Cloud Foundry is production.

- Before it went, `joshlong.com`, `api.joshlong.com` (`developer-advocacy`) and `bootifulpodcast.fm` (`bootiful-podcast`) had already moved to the foundation; `authorization.joshlong.com` no longer resolves.
- Its Ingresses, global static IPs and certificates are gone too, so nothing of it is still billed.
- Production's secrets live on in the Bitwarden item `mogul-production-secrets` (see "Bitwarden" above), copied from the cluster's `mogul-secrets` by `pipeline/bin/secrets-to-bitwarden.sh`.
- `k8s-pipeline` is unused, and its `Deploy` workflow is disabled.
- Processors no longer autoscales (item 1 under "Still open").
