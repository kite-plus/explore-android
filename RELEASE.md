# Releasing

Cutting a release is: write both changelogs, tag and push. The release workflow does the rest: it runs CI's checks, builds the release APK, signs it when signing is set up, and publishes the GitHub Release.

## What CI checks

`.github/workflows/ci.yml` runs on every push to `main` and every pull request, on an Ubuntu runner:

- **Review** (`scripts/check-comments.py`): code and resource comments in plain ASCII English.
- **Lint**: Android lint, which also fails on a string that has no Simplified Chinese translation.
- **Test**: the unit tests on the JVM.
- **Release build**: the release build with code shrinking, which catches what breaks only there.

## 1. Write the changelogs

In `CHANGELOG.md` and `CHANGELOG.zh-CN.md`, add a `## [<version>] - <date>` section below the `Unreleased` (`未发布`) heading, which stays and is left empty, and move that heading's items into it.

Start the section with a sentence or two on what the release is about, then group the changes under Added, Changed and Fixed (新增、变更、修复). Write for the people who use the app, not in commit subjects.

The release notes are built from these sections by `scripts/release-notes.sh`: the Chinese section is the body of the GitHub Release, the English one is linked from it, and a compare link with the previous tag follows. To see what the release will carry:

```bash
PREVIOUS_TAG=v0.1.0 sh scripts/release-notes.sh v0.1.1
```

A version without a section in both files fails the release before anything is built.

## 2. Tag and push

```bash
git tag -a v<version> -m v<version>
git push origin main v<version>
```

The tag sets the version name, and the workflow's run number sets the version code, so the project's own version only matters for local builds. A tag with a `-` suffix, such as `v0.2.0-rc.1`, ships as `0.2.0` and makes a prerelease on GitHub.

Pushing the tag starts `.github/workflows/release.yml`:

1. CI's checks, as above.
2. The release notes, from the changelogs.
3. The release build. With signing set up (below), the APK is signed and attached to the release. Without it, the build still runs to prove the release builds, and the run's summary says nothing was attached.
4. The GitHub Release `Kite for Android v<version>` with the notes. Re-running the workflow updates it instead of failing.

A manual run of the workflow builds without signing or publishing.

## Setting up signing

This is done once. The key signs every future update, so it must be kept safe: an app signed with another key cannot update the installed one.

1. Create a keystore, outside the repository:

   ```bash
   keytool -genkeypair -v -keystore kite-release.jks -alias kite -keyalg RSA -keysize 4096 -validity 10000
   ```

2. In the GitHub repository, under Settings, Secrets and variables, Actions, add these secrets:

   | Name | Value |
   |---|---|
   | `ANDROID_KEYSTORE_BASE64` | the keystore as one line: `base64 -i kite-release.jks` |
   | `ANDROID_KEYSTORE_PASSWORD` | the keystore's password |
   | `ANDROID_KEY_ALIAS` | `kite`, or the alias chosen above |
   | `ANDROID_KEY_PASSWORD` | the key's password |

3. Push the next tag. The run's summary says whether the APK was signed.

To sign a local release build, put the same values in `keystore.properties` at the repository's root (Git ignores it) as `storeFile`, `storePassword`, `keyAlias` and `keyPassword`, then run `./gradlew assembleRelease`.

Publishing on Google Play needs a developer account and the listing, and uploads an Android App Bundle (`./gradlew bundleRelease`) rather than the APK.

To keep `main` green, require the Check job in a branch protection rule for `main`.

## Kite Plus sign-in and App Links

Signing in with Kite Plus on explore.kite.plus comes back to the app at `https://explore.kite.plus/app/auth/done`, which the app claims with an App Link (`android:autoVerify` in `AndroidManifest.xml`). Android checks the claim against `https://explore.kite.plus/.well-known/assetlinks.json`, which Explore publishes once the server has these settings (Explore's docs/design/accounts.md, section 1.1):

| Variable | Value |
|---|---|
| `EXPLORE_ANDROID_PACKAGE` | `plus.kite.explore`, the default |
| `EXPLORE_ANDROID_CERT_SHA256` | the SHA-256 fingerprint of the release signing certificate, 32 hex pairs joined by colons; several are separated by commas, such as the upload key and Google Play's app signing key |

The fingerprint can only be set once the release keystore exists (see Setting up signing above). Read it with `keytool -list -v -keystore kite-release.jks -alias kite` (the SHA256 line), or from a signed APK with `apksigner verify --print-certs`. With Play App Signing, the key Play signs with is listed in the Play Console under App integrity.

Set these on explore.kite.plus before or together with turning on Kite Plus sign-in there. Publishing the file also makes the server refuse the `explore://` callback, which any app could register, and until Android has verified the claim, signing in lands on the server's "Finish signing in in the Kite app" page instead of returning to the app. After installing a release build, check the claim:

```bash
adb shell pm get-app-links plus.kite.explore          # explore.kite.plus: verified
adb shell pm verify-app-links --re-verify plus.kite.explore
```

Debug builds (`plus.kite.explore.dev`, signed with the debug key) are not in the file. To try Kite Plus sign-in on explore.kite.plus with one, approve the link on the device by hand:

```bash
adb shell pm set-app-links-user-selection --user cur --package plus.kite.explore.dev true explore.kite.plus
```

Any other server sends sign-in back to `explore://app/auth/done` and needs nothing set up. Debug builds also log the address that starts a sign-in (tag `KiteSignIn`), which holds only the PKCE challenge, so the browser part can be finished on another browser when the device's own cannot be used.

Signed-in requests send both the Bearer token and the `explore_session` cookie, the second with the CSRF header on writes, because Explore servers released before Kite Plus sign-in read only the cookie. Once those servers are no longer in use, `ApiClient.sessionHeaders` can drop the cookie and the CSRF header.
