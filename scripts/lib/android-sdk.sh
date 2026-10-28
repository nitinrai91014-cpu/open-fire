# shellcheck shell=bash
# Helpers shared by the APK signing/verification scripts.
#
# The Android SDK build-tools are not on PATH in a normal install, so every
# script that needs `apksigner`, `zipalign` or `aapt2` has to look for them.
# Keep this file side-effect free: it only defines functions.

# Print the Android SDK root, or return 1 when none is configured.
android_sdk_root() {
  local candidate
  for candidate in \
    "${ANDROID_HOME:-}" \
    "${ANDROID_SDK_ROOT:-}" \
    "${HOME:-}/Android/Sdk" \
    "${HOME:-}/Library/Android/sdk" \
    "/usr/lib/android-sdk" \
    "/opt/android-sdk" \
    "/opt/android-sdk-linux"; do
    if [ -n "$candidate" ] && [ -d "$candidate" ]; then
      printf '%s\n' "$candidate"
      return 0
    fi
  done
  return 1
}

# Sort build-tools version strings. BSD `sort` has no -V, so fall back to a plain
# sort there instead of failing.
sort_versions() {
  sort -V 2>/dev/null || sort
}

# Locate an SDK tool: PATH first, then the newest build-tools directory.
#
# Newest matters: `apksigner` from an old build-tools cannot read keystores or
# signature schemes produced by a newer one, and `zipalign` older than
# build-tools 26 does not understand APK Signing Blocks.
find_android_tool() {
  local tool="$1" sdk_root newest="" newest_version="" build_tools_dir version
  if command -v "$tool" >/dev/null 2>&1; then
    command -v "$tool"
    return 0
  fi
  if sdk_root="$(android_sdk_root)"; then
    for build_tools_dir in "$sdk_root"/build-tools/*/; do
      [ -x "${build_tools_dir}${tool}" ] || continue
      version="$(basename "$build_tools_dir")"
      if [ -z "$newest" ] ||
        [ "$(printf '%s\n%s\n' "$newest_version" "$version" | sort_versions | tail -n 1)" = "$version" ]; then
        newest="${build_tools_dir}${tool}"
        newest_version="$version"
      fi
    done
    if [ -n "$newest" ]; then
      printf '%s\n' "$newest"
      return 0
    fi
  fi
  return 1
}

# Print the APK's minSdkVersion when aapt/aapt2 is available, else the fallback.
# Signing for a slightly lower API level than the manifest asks for only means an
# extra v1 (JAR) signature is written, which legacy install/update paths want.
apk_min_sdk_version() {
  local apk="$1" fallback="${2:-23}" aapt aapt_path output
  for aapt in aapt2 aapt; do
    aapt_path="$(find_android_tool "$aapt" || true)"
    if [ -n "$aapt_path" ]; then
      output="$("$aapt_path" dump badging "$apk" 2>/dev/null || true)"
      if printf '%s' "$output" | grep -q "sdkVersion:"; then
        printf '%s' "$output" | sed -n "s/.*sdkVersion:'\([0-9]\{1,\}\)'.*/\1/p" | head -n 1
        return 0
      fi
    fi
  done
  printf '%s\n' "$fallback"
}

# True when the file carries the APK Signing Block magic right before the ZIP
# Central Directory (that is where v2/v3/v3.1 signatures live).
apk_has_signing_block() {
  local apk="$1"
  tail -c 65600 "$apk" | grep -qa "APK Sig Block 42"
}

# True when the APK contains a JAR (v1) signature: META-INF/*.SF plus *.RSA.
apk_has_v1_signature() {
  local apk="$1" names
  names="$(unzip -Z1 "$apk" 2>/dev/null || true)"
  printf '%s\n' "$names" | grep -q '^META-INF/.*\.\(SF\|RSA\|DSA\|EC\)$'
}
