#!/usr/bin/env bash
set -euo pipefail

SETTINGS="${MOGUL_HOME}/workspace/bin/settings.xml"

echo "deploying $(pwd) as ${GH_USER} using ${SETTINGS}"
./mvnw -s "$SETTINGS" deploy "$@"
