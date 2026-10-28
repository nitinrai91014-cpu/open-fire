#!/usr/bin/env bash
#
# Sign an unsigned release APK so that it can be installed on any supported
# Android version, then prove that it is installable.
#
# Why this script exists
# ----------------------
# Signing an APK "by hand" is where installable builds turn into
# "The package appears to be invalid":
#
#   * signing with jarsigner / an old GUI signer produces a JAR (v1) signature
#     only, and Android 11+ refuses v1-only packages when targetSdk >= 30;
#   * running zipalign AFTER apksigner destroys the v2/v3 signature;
#   * signing an APK that is already signed leaves stale META-INF signature
#     files behind, so verification fails on the device.
#
# The script enforces the order that Android requires - align, then sign, then
# verify - and refuses to produce an APK that would be rejected.
#
# Usage:
#   scripts/sign-release-apk.sh <unsigned.apk> [--out <signed.apk>]
#                               [--ks <keystore>] [--ks-alias <alias>]
#                               [--ks-pass <password>] [--key-pass <password>]
#                               [--ks-type <JKS|PKCS12>] [--min-sdk-version <n>]
#
# Without --ks the public CI test key in .github/ci/open-fire-test.p12 is used,
# which is fine for installing on your own device and for sharing test builds,
# but must never be used to publish an app.
#
# Exit codes: 0 = signed and installable, 1 = failure, 2 = usage/tooling error.

set -euo pipefail

script_dir="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repo_root="$(cd -- "$script_dir/.." && pwd)"
# shellcheck source=lib/android-sdk.sh
. "$script_dir/lib/android-sdk.sh"

CI_TEST_KEYSTORE="$repo_root/.github/ci/open-fire-test.p12"
CI_TEST_KEYSTORE_PASSWORD="openfireci"
CI_TEST_KEY_ALIAS="open-fire-ci"

input_apk=""
output_apk=""
keystore="${KEYSTORE_PATH:-}"
keystore_type=""
keystore_alias="${KEY_ALIAS:-}"
keystore_password="${STORE_PASSWORD:-}"
key_password="${KEY_PASSWORD:-}"
min_sdk_version=""

usage() {
  cat <<'EOF' >&2
Sign an unsigned release APK (align, then sign with v1+v2+v3, then verify).

Usage:
  scripts/sign-release-apk.sh <unsigned.apk> [options]

Options:
  --out <file.apk>        where to write the signed APK
                          (default: the input name without "-unsigned")
  --ks <keystore>         upload keystore (default: $KEYSTORE_PATH, else the
                          public CI test key .github/ci/open-fire-test.p12)
  --ks-type <type>        JKS or PKCS12 (default: inferred from the extension)
  --ks-alias <alias>      key alias (default: $KEY_ALIAS, else the test key alias)
  --ks-pass <password>    keystore password (default: $STORE_PASSWORD)
  --key-pass <password>   key password (default: $KEY_PASSWORD, else --ks-pass)
  --min-sdk-version <n>   lowest API level to sign for (default: from the APK)
  -h, --help              show this help

Exit codes: 0 = signed and installable, 1 = failure, 2 = usage/tooling error.
EOF
}

die() {
  printf 'error: %s\n' "$*" >&2
  exit 1
}

while [ "$#" -gt 0 ]; do
  case "$1" in
    --out) output_apk="${2:-}"; shift ;;
    --ks) keystore="${2:-}"; shift ;;
    --ks-type) keystore_type="${2:-}"; shift ;;
    --ks-alias) keystore_alias="${2:-}"; shift ;;
    --ks-pass) keystore_password="${2:-}"; shift ;;
    --key-pass) key_password="${2:-}"; shift ;;
    --min-sdk-version) min_sdk_version="${2:-}"; shift ;;
    -h|--help) usage; exit 0 ;;
    -*) printf 'error: unknown option %s\n\n' "$1" >&2; usage >&2; exit 2 ;;
    *)
      if [ -n "$input_apk" ]; then
        printf 'error: only one input APK can be signed at a time\n' >&2
        exit 2
      fi
      input_apk="$1"
      ;;
  esac
  shift
done

[ -n "$input_apk" ] || { usage >&2; exit 2; }
[ -f "$input_apk" ] || die "input APK not found: $input_apk"
unzip -tqq "$input_apk" >/dev/null 2>&1 || die "$input_apk is not a readable ZIP/APK"

apksigner_tool="$(find_android_tool apksigner || true)"
zipalign_tool="$(find_android_tool zipalign || true)"
if [ -z "$apksigner_tool" ] || [ -z "$zipalign_tool" ]; then
  printf 'error: apksigner/zipalign were not found.\n' >&2
  printf '       Set ANDROID_HOME to your Android SDK (the tools live in build-tools/<version>/).\n' >&2
  exit 2
fi

# Default output name: drop the "-unsigned" marker so the result is obviously the
# installable artifact (open-fire-release-unsigned.apk -> open-fire-release.apk).
if [ -z "$output_apk" ]; then
  case "$input_apk" in
    *-unsigned.apk) output_apk="${input_apk%-unsigned.apk}.apk" ;;
    *.apk) output_apk="${input_apk%.apk}-signed.apk" ;;
    *) output_apk="$input_apk.signed.apk" ;;
  esac
fi
if [ "$output_apk" = "$input_apk" ]; then
  die "--out must differ from the input APK (the input stays pristine for re-signing)"
fi

# Signing the wrong artifact is the most common way to get "invalid package".
if apk_has_signing_block "$input_apk" || apk_has_v1_signature "$input_apk"; then
  die "$input_apk is already signed. Signing an already-signed APK leaves its stale META-INF signature files behind and zipalign would invalidate the existing v2/v3 block, which is exactly how a package turns into 'The package appears to be invalid'. Start from the *-unsigned.apk artifact (or from 'assembleRelease' with signing disabled)."
fi

# Default keystore: the CI test key unless the caller configured an upload key.
using_ci_test_key=0
if [ -z "$keystore" ]; then
  keystore="$CI_TEST_KEYSTORE"
  using_ci_test_key=1
fi
[ -f "$keystore" ] || die "keystore not found: $keystore"
if [ "$using_ci_test_key" -eq 1 ]; then
  keystore_alias="${keystore_alias:-$CI_TEST_KEY_ALIAS}"
  keystore_password="${keystore_password:-$CI_TEST_KEYSTORE_PASSWORD}"
  key_password="${key_password:-$CI_TEST_KEYSTORE_PASSWORD}"
  printf 'warning: no keystore configured (--ks / KEYSTORE_PATH); signing with the public CI test key.\n' >&2
  printf '         The APK installs, but it cannot be published and it will not update an app signed with another key.\n' >&2
else
  [ -n "$keystore_password" ] || die "no keystore password: pass --ks-pass or set STORE_PASSWORD"
  if [ -z "$keystore_alias" ]; then
    keystore_alias="$(keytool -list -keystore "$keystore" ${keystore_type:+-storetype "$keystore_type"} -storepass "$keystore_password" 2>/dev/null \
      | sed -n 's/^\([^,]*\),.*PrivateKeyEntry.*/\1/p' | head -n 1 || true)"
    [ -n "$keystore_alias" ] || die "no --ks-alias given and no private key alias could be read from $keystore"
    printf 'info: using key alias %s\n' "$keystore_alias" >&2
  fi
  if [ -z "$key_password" ]; then
    key_password="$keystore_password"
  fi
fi

# A .p12/.pfx file is certainly PKCS12. Any other name is guessed by apksigner and
# retried below, because "my-upload-key.jks" is a PKCS12 file in most setups that
# were created with a modern keytool.
explicit_keystore_type="$keystore_type"
if [ -z "$keystore_type" ]; then
  case "$keystore" in
    *.p12|*.pfx|*.pkcs12|*.P12|*.PFX) keystore_type="PKCS12" ;;
  esac
fi

if [ -z "$min_sdk_version" ]; then
  # Signing for API 23 rather than the manifest's 24 only means an extra v1 (JAR)
  # signature is produced, which keeps legacy install/update paths working.
  min_sdk_version="$(apk_min_sdk_version "$input_apk" 23)"
fi

work_dir="$(mktemp -d "${TMPDIR:-/tmp}/open-fire-sign-XXXXXX")"
cleanup() { rm -rf "$work_dir"; }
trap cleanup EXIT

aligned_apk="$work_dir/aligned.apk"

# Step 1: align BEFORE signing.  -p page-aligns uncompressed .so files (16 KiB
# page devices); it is a no-op when the APK has no native libraries.
printf 'Aligning %s ...\n' "$(basename "$input_apk")"
"$zipalign_tool" -p -f 4 "$input_apk" "$aligned_apk"

# Step 2: sign with v1 + v2 + v3 and no v4 (which would write a stray .idsig).
extra_sign_args=()
if "$apksigner_tool" sign --help 2>&1 | grep -q -- "--v4-signing-enabled"; then
  extra_sign_args+=(--v4-signing-enabled false)
fi
printf 'Signing with %s (alias %s) ...\n' "$(basename "$keystore")" "$keystore_alias"

sign_log="$work_dir/apksigner-sign.log"

# apksigner sign, optionally with an explicit keystore type.
run_apksigner_sign() {
  local ks_type="$1"
  local type_args=()
  rm -f "$output_apk"
  if [ -n "$ks_type" ]; then
    type_args=(--ks-type "$ks_type")
  fi
  "$apksigner_tool" sign \
    --ks "$keystore" \
    ${type_args[@]+"${type_args[@]}"} \
    --ks-key-alias "$keystore_alias" \
    --ks-pass "pass:$keystore_password" \
    --key-pass "pass:$key_password" \
    --v1-signing-enabled true \
    --v2-signing-enabled true \
    --v3-signing-enabled true \
    --min-sdk-version "$min_sdk_version" \
    ${extra_sign_args[@]+"${extra_sign_args[@]}"} \
    --out "$output_apk" \
    "$aligned_apk" >"$sign_log" 2>&1
}

signed_ok=0
if [ -n "$explicit_keystore_type" ] || [ -n "$keystore_type" ]; then
  if run_apksigner_sign "$keystore_type"; then
    signed_ok=1
  fi
else
  for candidate_type in "" PKCS12 JKS; do
    if run_apksigner_sign "$candidate_type"; then
      signed_ok=1
      if [ -n "$candidate_type" ]; then
        printf 'info: apksigner needed --ks-type %s for this keystore\n' "$candidate_type" >&2
      fi
      break
    fi
  done
fi

if [ "$signed_ok" -ne 1 ]; then
  rm -f "$output_apk"
  sed 's/^/  apksigner: /' "$sign_log" >&2 || true
  die "apksigner could not sign with $keystore. Check the keystore password and, if the file is a PKCS12 keystore with a .jks name (or the other way round), pass --ks-type PKCS12 or --ks-type JKS."
fi

# Step 3: verify the finished package exactly like Android would.
printf 'Verifying %s ...\n' "$(basename "$output_apk")"
"$script_dir/verify-installable-apk.sh" --require-signature "$output_apk"

if [ "$using_ci_test_key" -eq 1 ]; then
  printf '\nThe APK is signed with the public CI test key.\n'
else
  printf '\nThe APK is signed with your upload key.\n'
fi
printf 'Install it with:  adb install -r %s\n' "$output_apk"
printf 'On the device: copy the file over, tap it, and allow installing unknown apps.\n'
