#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 4 ]]; then
    echo "Usage: bash tools/verify-release-apk.sh APK CERT_SHA256 VERSION_CODE VERSION_NAME" >&2
    exit 2
fi

apk=$1
expected_signer=$2
version_code=$3
version_name=$4
if [[ ! "$expected_signer" =~ ^[[:xdigit:]]{64}$ || ! "$version_code" =~ ^[1-9][0-9]*$ || ! "$version_name" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
    echo "Supply a 64-character signer fingerprint, a positive version code, and MAJOR.MINOR.PATCH." >&2
    exit 2
fi

sdk=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
if [[ -z "$sdk" ]]; then
    echo "Set ANDROID_HOME or run within the Nix devshell." >&2
    exit 2
fi
build_tools="$sdk/build-tools/37.0.0"

signing=$("$build_tools/apksigner" verify --print-certs "$apk")
actual_signer=$(
    printf '%s\n' "$signing" |
        awk '/Signer.*certificate SHA-256 digest: / {sub(/^.*certificate SHA-256 digest: /, ""); print tolower($0)}' |
        sort -u
)
expected_signer=$(printf '%s' "$expected_signer" | tr '[:upper:]' '[:lower:]')
if [[ "$actual_signer" != "$expected_signer" ]]; then
    echo "The APK must have exactly the expected signing certificate." >&2
    exit 1
fi

badging=$("$build_tools/aapt" dump badging "$apk")
expected="package: name='io.github.marcus_gt.maekster' versionCode='$version_code' versionName='$version_name'"
if [[ "$badging" != "$expected "* ]]; then
    echo "The APK package or version does not match the intended maekster release." >&2
    exit 1
fi

printf 'Verified maekster %s (%s), signer SHA-256 %s\n' "$version_name" "$version_code" "$actual_signer"
