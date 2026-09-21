#!/usr/bin/env bash
set -euo pipefail

# deploys the Maven project in the current directory to GitHub Packages.
#
# credentials are not stored anywhere: settings.xml (next to this script,
# version controlled) resolves ${env.GH_USER} / ${env.GH_TOKEN} for the
# <server id="github"> entry, so both must be exported first.

: "${GH_USER:?GH_USER is not set}"
: "${GH_TOKEN:?GH_TOKEN is not set}"

BIN_DIR="$(cd -P "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SETTINGS="${BIN_DIR}/settings.xml"

[ -f "$SETTINGS" ] || { echo "no settings.xml at ${SETTINGS}" >&2; exit 1; }
[ -x ./mvnw ] || { echo "no ./mvnw in $(pwd); run this from a project root" >&2; exit 1; }

echo "deploying $(pwd) as ${GH_USER} using ${SETTINGS}"
./mvnw -s "$SETTINGS" deploy "$@"
