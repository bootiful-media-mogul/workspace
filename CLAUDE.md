# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Read `AGENTS.md` in this directory too; it is the shared agent guidance for this workspace and this file builds on it.

## Workspace shape

`~/code/mogul` is not a Git repository and not a Maven reactor.
Each child directory is its own Git repository (cloned from `github.com/bootiful-media-mogul` by `workspace/bin/start.java`).
Run `git` and build commands from inside the child repository that owns the code, and validate each touched repository separately.

The root `mise.toml` is copied from `workspace/mise.toml`.
It pins GraalVM CE 25, puts `workspace/bin` on the `PATH`, and sets `MAVEN_ARGS` to use `workspace/bin/settings.xml` (GitHub Packages, where the shared libraries are published).
Secrets are not loaded by mise; a shell needs `bw-unlock` then `source workspace/bin/env.sh` (Bitwarden-backed). Never print or commit those values.

## Commands

Java repositories (`api`, `gateway`, `processors`, `utils`, `storage`, `jobrunr`, `settings-client`, `cleanvoice-client`), run from the repository directory:

```bash
./mvnw spring-javaformat:apply              # format; required, the build checks it
./mvnw test                                 # all tests
./mvnw test -Dtest=PodcastIntegrationTest   # one class
./mvnw test -Dtest=PodcastIntegrationTest#methodName
validate.sh                                 # format + clean package (on PATH via mise)
native.sh <binary-name>                     # GraalVM native compile, then run the binary
deploy_library.sh                           # publish a shared library to GitHub Packages
```

The applications depend on `1.0.0-SNAPSHOT` versions of the shared libraries (`utils`, `storage`, `jobrunr`, `settings-client`).
After changing a library, `./mvnw install` it (or `deploy_library.sh`) before building the application that consumes it.

Local infrastructure (Postgres/pgvector, Redis, RabbitMQ, all with `mogul`/`mogul` credentials): `setup_local_infrastructure.sh`.
`setup_local_dev_from_prod_db.sh` loads production data into the local database; many `api` tests expect that database and the secrets to be present.

Client (`client/`, Vue 3 + Vite + TypeScript):

```bash
npm install
npm run dev          # or ./dev.sh (install, prettier, dev server on 127.0.0.1)
npm run build        # vue-tsc type-check + vite build
npm run test:unit    # vitest
npm run lint         # eslint --fix
npm run format       # prettier
```

Deployment to production (GKE Autopilot) is `pipeline/bin/deploy.sh`, which renders `pipeline/bin/k8s/carvel` with ytt and deploys `api gateway client processors`.
Native-image builds and deploys take 15-20 minutes.
`cf/` is a separate trial of Cloud Foundry on GKE; see `cf/README.md`.

## Architecture

Services:

- `api` - the main Spring Boot service and the rich domain. Spring GraphQL (`src/main/resources/graphql/schema.graphqls`), `JdbcClient` with row mappers / result-set extractors, Flyway migrations in `src/main/resources/db/migration`, Caffeine caches with cross-node eviction, and server-to-browser notifications (Ably).
- `gateway` - Spring MVC + Spring Cloud Gateway on virtual threads; OAuth login and routing to `api` and `client`.
- `processors` - headless worker with no web server, for CPU-heavy work (media normalization, audio production, transcription).
- `client` - the Vue SPA ("studio"); urql GraphQL client, Pinia, vue-i18n.
- `clip` - Chrome extension.

Shared libraries: `jobrunr` (autoconfiguration with a custom JobRunr `StorageProvider` so each service only claims jobs it can run from the shared JobRunr tables), `storage` (S3-style managed files), `settings-client`, `utils`, and `cleanvoice-client` (a standalone, Maven Central-published Cleanvoice API client).

api to processors protocol: `api` sends a RabbitMQ message whose headers carry input/output bucket and key, a correlation id, the processor id, and context.
`processors` turns it into a JobRunr job that looks up the `Processor` bean by id, and replies on a reply queue with the same headers plus `success` and an optional error.
There are no extra tracking tables - RabbitMQ and JobRunr provide durability, and handlers must be idempotent so replays are safe.

The domain pattern (read `api/docs/domain-pattern.md` before touching a cross-cutting concern):
cross-cutting concerns are marker interfaces named `{Domain}able` (`Publishable`, `Transcribable`, `Searchable`, `Notable`, `Composable`) implemented by entities such as `Episode`, `Post`, and `Segment`.
Each concern has a domain record (`Publication`, `Transcript`, `Note`) whose rows soft-link to the owning entity through `payload` (serialized id) and `payload_class` rather than a foreign key, and per-entity resolvers that `AbstractDomainService` picks at runtime.
`Publication`s are delivered by `PublisherPlugin` beans (Ayrshare social, Podbean, WordPress, and others).
`Mogul` is the user entity; almost every table carries `mogul_id`.

## Constraints that are easy to miss

- Every service ships as a GraalVM native image: avoid JobRunr lambdas (use `JobRequest`/handler beans), and keep reflection, proxies, and resources AOT-friendly (see `DomainAotConfiguration` in `api`).
- Never edit a committed Flyway migration; add a new `V{n}__*.sql`.
- Kubernetes config must not reference Java class names; use stable indirection such as module names or database views.
- Avoid N+1 queries; prefer batch loads and `ResultSetExtractor`s over per-row lookups.
- Services should run with two or more replicas, so do not rely on in-memory, single-node state for correctness.
