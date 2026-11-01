#!/usr/bin/env bash
#
# puts the workspace-level files from this repository into ~/code/mogul. bin/start.java does
# this too, after cloning everything; this is for when the repositories are already there.

set -euo pipefail

MOGUL_HOME="${MOGUL_HOME:-$HOME/code/mogul}"

cp "${MOGUL_HOME}/workspace/mise.toml" "${MOGUL_HOME}/mise.toml"
# linked, not copied, so editing ~/code/mogul/CLAUDE.md edits the file git sees.
for doc in CLAUDE.md AGENTS.md; do
  ln -sfn "workspace/${doc}" "${MOGUL_HOME}/${doc}"
done
echo "set up ${MOGUL_HOME}"
