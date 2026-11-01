# Agent guidance for the Mogul workspace

## Workspace layout

This directory is a workspace of independent Git repositories, not a Git repository or a single Maven reactor. Run Git and build commands from the relevant child directory. Check that repository's status before editing, and keep changes scoped to the repository that owns the code.

- `api/`: main Spring Boot API and domain logic. Read `api/docs/domain-pattern.md` before changing a cross-cutting domain concern.
- `gateway/`: gateway, authentication, and request routing.
- `processors/`: background media and transcription processing.
- `utils/`, `storage/`, `jobrunr/`, `settings-client/`: shared Java libraries used by the applications.
- `clip/`: Chrome extension; see its README for local loading instructions.
- `pipeline/`: production deployment definitions and scripts.
- `workspace/`: local setup and maintenance scripts; `workspace/bin/` contains shared commands.
- `github-actions-setup-*/`: reusable GitHub Actions.

`client/` may be empty in a local checkout; inspect it before assuming an application is present.

## Development and validation

The Java repositories use Java 25, Maven wrappers, Spring Boot, and Spring JavaFormat. The root `mise.toml` selects the Java and JBang tools and sets Maven arguments for the shared settings file. Use each repository's `./mvnw` from that repository. For a Java change, run the relevant focused tests and then the appropriate module build or test command (for example, `./mvnw test`). Use `./mvnw spring-javaformat:apply` for Java formatting; review the resulting diff because this command edits files.

Some application tests and local scripts depend on infrastructure or secrets. If a check cannot run for that reason, report the exact command and failure rather than treating it as a code failure. Do not print, copy into source, or commit values loaded by `workspace/bin/env.sh` or other local secret tooling.

There is no workspace-wide test command. When a change spans repositories, validate each affected repository independently and account for their local dependency versions.

## Working with Josh

Josh builds and owns Mogul alone.
Treat him as a peer and skip Spring basics; he is an expert in Spring (Boot, Integration, Cloud Gateway, GraphQL, AOT and GraalVM), PostgreSQL and Kubernetes, and less at home in GKE, gcloud and KEDA operational detail.
He types fast and casually ("u", "ur", "yah"); read for intent.

- When he is thinking out loud ("im asking not telling", "whaddya think?", "lets talk this out", "before u change everything"), answer with analysis and a recommendation, and implement only once he says go. Explicit instructions are just done.
- Number findings, so he can pick ("fix 1 and 2, then lets talk about 3"), and keep the list straight within a session.
- Keep scope tight. "Lets not worry about X yet" means build only the minimal piece.
- Nothing important should live only on one machine: he changes laptops at least once a year. Durable knowledge goes into a repository (this file, a README, `docs/`), secrets into Bitwarden, and never only into an agent's local memory.

## Design rules

These are on top of the constraints in `CLAUDE.md`, and each comes from past corrections.

- **Primitives in service APIs.** Service methods and cross-module messages take ids, strings, numbers and dates, not rich domain objects. When an optimization seems to need a rich object passed in, offer a primitive overload instead. api to processors messages carry only minimal strings, as headers.
- **No workflow state tables.** RabbitMQ redelivery and JobRunr's own tables already provide durability, so don't add tables like `processor_execution` to track async work. The client treats work as not done until api gets the reply. Fire-and-forget methods can return `void`. Small state goes on an existing entity (for example a field on `ManagedFile`). Propose the minimal version first.
- **Be kind to PostgreSQL.** Page loads should issue a constant number of queries however many rows there are. Batch-load by ids (for example `Map<publishableId, Collection<Publication>>`), and prefer `ResultSetExtractor`s to row mappers that call other services per row. Point out N+1s when touching existing code. Indexes go in a new migration.
- **No read-through caches unless asked.** One was rolled back in `DefaultPodcastService`. Aggressive per-mogul Caffeine caching of settings is wanted, though: load all of a mogul's settings at once, and invalidate per key on set.
- **Config refers to code through stable names.** When Kubernetes or other config has to refer to code, add an indirection (a view, label or module name) instead of class or method names. The model is the `jobrunr_jobs_by_module` view, which KEDA queries by module (`api`, `processors`), with logic like coalescing kept in the view.
- **Assume several replicas.** api and processors are meant to run with two or more instances; flag new in-memory state, caches without cross-node eviction, or scheduled work that assumes a single instance.
- **Native images and slow deploys.** A deploy takes 15-20 minutes per module, so get changes right before a push, and make database objects idempotent (`create or replace`, `if not exists`), because Josh sometimes applies them to production directly rather than wait.

## UI conventions (client)

- Buttons are never blue. All buttons are black, styled like the existing "re-transcribe" button. The Ayrshare publish button has regressed to blue before.
- User-facing strings go in the i18n messages in `client/src/main.ts`, never hardcoded.
- Reuse the existing CSS helpers (there is a gutter class) for spacing, don't stack controls without spacing, and center small footer-like elements.
- Avoid layout reflow and needless scrollbars (for example textareas overflowing their container).
- Keep screens uncluttered: extract shared read-only render components, and add flags to hide duplicate sections.
- Datepickers (v-calendar) open on click, not on hover or focus.
- Check new UI against these before calling it done, and look at screenshots when Josh sends them.

## Local environment

- The local PostgreSQL (`localhost`, `mogul`/`mogul`, set up by `setup_local_dev_from_prod_db.sh`) holds a copy of production data. It is fine to use for benchmarking, `EXPLAIN` plans and query counts.
- Never run full-context api tests against Josh's live local RabbitMQ.
- If gcloud or kubectl keeps asking to re-authenticate, run `gcloud auth login` and `gcloud auth application-default login`.
- Production is GKE Autopilot in GCP project `joshlong` (context `gke_joshlong_us-east4_joshlong`). Images are in `us-docker.pkg.dev/joshlong/mogul-artifact-registry/<module>`, and assets are served through CloudFront (`pipeline/bin/cloudfront_cache_policy.sh`). In ytt files under `pipeline/`, comments must start with `#!`, not `#`.
- `cf/` is a separate trial of Cloud Foundry on GKE, on its own cluster; read `cf/AGENTS.md` before working there.
