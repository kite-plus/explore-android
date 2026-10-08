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
