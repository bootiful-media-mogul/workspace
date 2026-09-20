#!/usr/bin/env bash
# Source this - do not run it - to put Mogul's secrets into the current shell:
#
#   bw-unlock            # unlocks the vault, from the bitwarden block in ~/.zshrc
#   source bin/env.sh    # this file
#
# It is deliberately not wired into mise.toml as an `_.source` script. mise re-runs those on
# essentially every shell prompt and re-runs them from a clean environment, so env.java's
# 5-6 seconds were being paid before every command and every command looked like it hung.
# Loaded here the variables land in the shell's own environment and stay - mise only strips
# variables it set itself.

_mogul_home="${MOGUL_HOME:-$HOME/code/mogul}"

if [ -z "${BW_SESSION:-}" ] || ! bw unlock --check >/dev/null 2>&1 </dev/null; then
  echo "mogul: bitwarden is locked - run 'bw-unlock' first" >&2
  unset _mogul_home
  return 1 2>/dev/null || exit 1
fi

# `eval "$(cmd)"` throws away cmd's exit status, so a failure would surface as a shell
# quietly missing half its variables. Assign, check, then eval.
if ! _mogul_env="$("$_mogul_home/workspace/bin/env.java" </dev/null)"; then
  echo "mogul: env.java failed, nothing loaded" >&2
  unset _mogul_home _mogul_env
  return 1 2>/dev/null || exit 1
fi

eval "$_mogul_env"
echo "mogul: loaded $(printf '%s' "$_mogul_env" | grep -c '^export ') variables"
unset _mogul_home _mogul_env
