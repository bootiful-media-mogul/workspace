#!/usr/bin/env bash


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
