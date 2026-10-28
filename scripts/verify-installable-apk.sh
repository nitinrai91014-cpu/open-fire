#!/usr/bin/env bash
#
# Explain why an APK will (or will not) install on an Android device.
#
# "The package appears to be invalid" (a.k.a. "App not installed") is what the
# Android package installer shows for a whole family of packaging problems, and
# it never says which one it hit. This script checks them one by one and prints a
# verdict, so a broken build is diagnosed before it reaches a phone:
#
#   1. ZIP integrity        - truncated download, broken central directory
#   2. required entries     - AndroidManifest.xml, resources.arsc, classes*.dex
#   3. resources.arsc       - must be ZIP_STORED + 4-byte aligned for targetSdk>=30
#   4. zipalign             - uncompressed entries must be 4-byte aligned
#   5. signature presence   - an unsigned APK never installs
#   6. signature schemes    - Android 11+ requires v2+ when targetSdk >= 30
#   7. signature validity   - verify for API 24 and for API 30..36
#
# Usage:
#   scripts/verify-installable-apk.sh [--require-signature] [--quiet] <file.apk>
#
# Exit codes: 0 = installable, 1 = at least one problem, 2 = usage/tooling error.

set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=lib/android-sdk.sh
. "$script_dir/lib/android-sdk.sh"

require_signature=0
quiet=0
apk=""

usage() {
  cat <<'EOF'
Verify that an APK is installable, and explain every reason it would not be.

Usage:
  scripts/verify-installable-apk.sh [--require-signature] [--quiet] <file.apk>

  --require-signature  the APK must be signed with v2+ (the installable artifact)
  --quiet              only print failures

Exit codes: 0 = installable, 1 = at least one problem, 2 = usage/tooling error.
EOF
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --require-signature) require_signature=1 ;;
    --quiet) quiet=1 ;;
    -h|--help) usage; exit 0 ;;
    -*) printf 'error: unknown option %s\n\n' "$1" >&2; usage >&2; exit 2 ;;
    *)
      if [ -n "$apk" ]; then
        printf 'error: only one APK can be verified at a time\n' >&2
        exit 2
      fi
      apk="$1"
      ;;
  esac
  shift
done

if [ -z "$apk" ]; then
  printf 'error: no APK given\n\n' >&2
  usage >&2
  exit 2
fi
if [ ! -f "$apk" ]; then
  printf 'error: %s does not exist\n' "$apk" >&2
  exit 2
fi
if [ ! -s "$apk" ]; then
  printf 'error: %s is empty; the download was truncated\n' "$apk" >&2
  exit 1
fi

problems=()
notes=()

ok() { [ "$quiet" -eq 1 ] || printf '  [ ok ] %s\n' "$*"; }
info() { [ "$quiet" -eq 1 ] || printf '  [info] %s\n' "$*"; }
bad() {
  printf '  [FAIL] %s\n' "$*" >&2
  problems+=("$*")
}

apksigner_tool="$(find_android_tool apksigner || true)"
zipalign_tool="$(find_android_tool zipalign || true)"

if [ -z "$apksigner_tool" ] || [ -z "$zipalign_tool" ]; then
  printf 'error: apksigner/zipalign were not found.\n' >&2
  printf '       Set ANDROID_HOME to your Android SDK (the tools live in build-tools/<version>/).\n' >&2
  printf '       Android Studio installs them with the SDK; `sdkmanager "build-tools;36.0.0"` adds them.\n' >&2
  exit 2
fi

if [ "$quiet" -eq 0 ]; then
  printf 'Verifying %s (%s)\n' "$apk" "$(du -h "$apk" | cut -f1)"
  printf '  apksigner: %s\n' "$apksigner_tool"
fi

# 1. ZIP integrity -------------------------------------------------------------
if unzip -tqq "$apk" >/dev/null 2>&1; then
  ok "ZIP archive is intact"
else
  bad "the file is a corrupt or truncated ZIP, so Android cannot parse it. Download it again and check the SHA-256."
fi

names="$(unzip -Z1 "$apk" 2>/dev/null || true)"

# 2. Required entries ---------------------------------------------------------
for required in AndroidManifest.xml resources.arsc; do
  if printf '%s\n' "$names" | grep -qx "$required"; then
    ok "$required present"
  else
    bad "$required is missing; this is not an installable APK"
  fi
done
if printf '%s\n' "$names" | grep -qE '^classes[0-9]*\.dex$'; then
  ok "classes.dex present"
else
  bad "no classes*.dex entry; this is not an installable APK"
fi

# 3. resources.arsc must be stored (Android 11+ / targetSdk 30+) --------------
arsc_line="$(unzip -v "$apk" 2>/dev/null | awk '$NF=="resources.arsc"' || true)"
if [ -n "$arsc_line" ]; then
  if printf '%s' "$arsc_line" | grep -q "Stored"; then
    ok "resources.arsc is stored uncompressed"
  else
    bad "resources.arsc is compressed. Apps targeting Android 11+ cannot be installed when resources.arsc is compressed (RESOURCES_ARSC_COMPRESSED)."
  fi
fi

# 3b. Native libraries (.so) must be stored uncompressed for extractNativeLibs=false
compressed_so="$(unzip -v "$apk" 2>/dev/null | grep -E '\.so$' | grep -v 'Stored' || true)"
if [ -n "$compressed_so" ]; then
  bad "native libraries (.so) are compressed. Android refuses to install packages when native libraries are compressed with extractNativeLibs=false."
fi

# 4. zipalign ----------------------------------------------------------------
if "$zipalign_tool" -c 4 "$apk" >/dev/null 2>&1; then
  ok "uncompressed entries are 4-byte aligned (zipalign -c 4)"
else
  bad "uncompressed entries are not 4-byte aligned. Align with 'zipalign -p 4' BEFORE signing; aligning afterwards destroys the signature."
fi

# 5/6/7. Signature -----------------------------------------------------------
signed=0
if verify_report="$("$apksigner_tool" verify --verbose "$apk" 2>&1)"; then
  signed=1
else
  signed=0
fi

scheme_ok() {
  printf '%s\n' "$verify_report" | grep -Eq "Verified using $1 scheme [^:]*: true"
}

if [ "$signed" -eq 1 ]; then
  v1=0; v2=0; v3=0
  if scheme_ok v1; then v1=1; fi
  if scheme_ok v2; then v2=1; fi
  if scheme_ok v3; then v3=1; fi

  if apk_has_signing_block "$apk"; then
    info "APK Signing Block present (this is where v2/v3 signatures live)"
  fi

  if [ "$v1" -eq 1 ]; then
    ok "APK Signature Scheme v1 (JAR signing): verified"
  else
    notes+=("no v1 signature: packages for Android 6.0 and older (and some update paths) need it")
  fi
  if [ "$v2" -eq 1 ]; then
    ok "APK Signature Scheme v2: verified"
  fi
  if [ "$v3" -eq 1 ]; then
    ok "APK Signature Scheme v3: verified"
  fi

  if [ "$v2" -eq 0 ] && [ "$v3" -eq 0 ]; then
    bad "signed with v1 (JAR) only. Apps that target Android 11+ must also be signed with APK Signature Scheme v2 or higher, otherwise Android 11+ refuses to install them. Sign with 'apksigner sign --v1-signing-enabled true --v2-signing-enabled true --v3-signing-enabled true'."
  fi

  # Verify the same way the platform does for every supported API range.
  for sdk in 24 30; do
    if "$apksigner_tool" verify --min-sdk-version "$sdk" --max-sdk-version 36 "$apk" >/dev/null 2>&1; then
      ok "signature verifies for API $sdk..36"
    else
      bad "signature does not verify for API $sdk..36"
    fi
  done

  cert_line="$("$apksigner_tool" verify --print-certs "$apk" 2>/dev/null | grep -m 1 'certificate DN' || true)"
  if [ -n "$cert_line" ]; then
    info "$cert_line"
    notes+=("an update must be signed with the same key as the installed app, otherwise Android reports a package conflict")
  fi
else
  if [ "$require_signature" -eq 1 ]; then
    bad "the APK is not signed (or its signature is invalid), so Android cannot install it. This is the most common cause of 'The package appears to be invalid'."
    first_line="$(printf '%s\n' "$verify_report" | grep -m 1 -v '^$' || true)"
    if [ -n "$first_line" ]; then
      info "apksigner: $first_line"
    fi
  else
    info "not signed: this is the distributor artifact; sign it with your upload key before installing"
  fi
fi

printf '\n'

if [ "${#problems[@]}" -gt 0 ]; then
  printf '%d problem(s) found, this APK will not install:\n' "${#problems[@]}" >&2
  for problem in "${problems[@]}"; do
    printf '  - %s\n' "$problem" >&2
  done
  exit 1
fi

if [ "$signed" -eq 1 ] || [ "$require_signature" -eq 1 ]; then
  printf 'Verdict: %s is signed and installable.\n' "$(basename "$apk")"
else
  printf 'Verdict: %s is a structurally valid but unsigned package; sign it before installing.\n' "$(basename "$apk")"
fi
if [ "${#notes[@]}" -gt 0 ] && [ "$quiet" -eq 0 ]; then
  printf 'Notes:\n'
  for note in "${notes[@]}"; do
    printf '  - %s\n' "$note"
  done
fi
