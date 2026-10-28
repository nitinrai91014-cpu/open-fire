#!/usr/bin/env bash
# shellcheck shell=bash
#
# Helpers for the release steps.
#
# The reason a release step failed normally lives in the step log. Turning it
# into a GitHub annotation as well keeps the failure diagnosable from the
# checks API and from `gh run view`, which is where build failures are usually
# inspected first.

# Run a command, keep its output in $RUNNER_TEMP, and annotate the step with the
# tail of that output when it fails.
run_annotated() {
  local label="$1"
  shift

  local log_file
  log_file="${RUNNER_TEMP:-/tmp}/$(printf '%s' "$label" | tr -c 'A-Za-z0-9._-' '-').log"

  printf '\n### %s\n$ %s\n' "$label" "$*"

  local had_errexit=0
  case "$-" in
    *e*) had_errexit=1 ;;
  esac
  set +e
  if [[ "$1" == *.sh && ! -x "$1" ]]; then
    bash "$@" 2>&1 | tee "$log_file"
  else
    "$@" 2>&1 | tee "$log_file"
  fi
  local status="${PIPESTATUS[0]}"
  if [ "$had_errexit" -eq 1 ]; then
    set -e
  fi

  if [ "$status" -ne 0 ]; then
    local message
    message="$(tail -c 1800 "$log_file" | tr '\r\n\t' '   ')"
    message="${message//'%'/'%25'}"
    message="${message//$'\r'/'%0D'}"
    printf '::error title=%s (exit %d)::%s\n' "$label" "$status" "$message"
    return "$status"
  fi
}
