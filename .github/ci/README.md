# Public CI test signing key

`open-fire-test.p12` is a **throwaway, intentionally public** signing key. It exists so
that every build — CI, a fresh clone, AI Studio — can produce an APK that actually
installs, instead of an unsigned file that Android rejects with
"The package appears to be invalid".

| | |
| --- | --- |
| File | `open-fire-test.p12` (PKCS#12) |
| Alias | `open-fire-ci` |
| Store / key password | `openfireci` |
| Subject | `CN=Open Fire CI Test Key, O=Open Fire, OU=CI test signing key - not for distribution, C=NP` |
| Validity | 30 years |
| Key | RSA 3072, SHA-256 |

## What it is for

* CI: when no `ANDROID_KEYSTORE_BASE64` secret is configured, the installable APK is signed
  with this key so users always get something they can install.
* Local builds: `./gradlew :app:assembleRelease` works on a fresh clone without any secret.
* Tests: the documented signing flow (`scripts/sign-release-apk.sh`) is exercised in CI with
  real `apksigner` runs.

## What it is NOT for

* Publishing to Google Play or distributing to users at scale. Anyone can sign with this key.
* Replacing an upload key. An update must be signed with the **same** key as the installed
  app; a build signed with this key cannot update an installation signed with your own key,
  and vice versa.

Configure real secrets to replace it in CI:

| Secret | Meaning |
| --- | --- |
| `ANDROID_KEYSTORE_BASE64` | base64 of your keystore (`base64 -w0 my-upload-key.jks`) |
| `ANDROID_KEYSTORE_TYPE` | `JKS` (default) or `PKCS12` |
| `ANDROID_KEY_ALIAS` | key alias, e.g. `upload` |
| `ANDROID_STORE_PASSWORD` | keystore password |
| `ANDROID_KEY_PASSWORD` | key password (defaults to the keystore password) |

Locally, the same key can be selected with `KEYSTORE_PATH` / `STORE_PASSWORD` /
`KEY_PASSWORD` / `KEY_ALIAS` when running Gradle, or with `--ks` /
`scripts/sign-release-apk.sh`.

Because the key is public, it is committed in plain text on purpose: it must never be used
for a build that is meant to be trusted.
