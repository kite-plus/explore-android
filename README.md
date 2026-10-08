<h1 align="center">Kite for Android</h1>

<p align="center">Discover what people publish, on Android phones and tablets.</p>

<p align="center">
  <strong>English</strong> · <a href="README.zh-CN.md">简体中文</a>
</p>

Kite is the native Android client for [Explore](https://github.com/kite-plus/explore), the stream of new posts from independent blogs, and the companion of [Kite for iOS](https://github.com/kite-plus/explore-ios). It is written in Kotlin with Jetpack Compose and Material 3 for Android 10 and later. Like the website, it shows titles, dates, short excerpts and thumbnails, and every post opens on the author's own site.

## Features

- **Discover**: Explore's three streams as tabs, as on the website: the latest posts from every listed blog, the recommended ones, and the blogs you follow. Swipe between them; the underline follows. Posts run newest first in one list, each saying when it came out, and filter by blog language and tag, with pull to refresh and endless paging. Notices Explore publishes sit between the latest posts, and its own notices open on a page in the app. Each post shows Explore's latest link check; posts that were never checked can be checked on the spot.
- **Following**: follow blogs and read their new posts in Discover's Following tab; manage the list under Me, import an OPML file from another feed reader, and export the list as OPML for any feed reader.
- **Blogs**: the directory, most recently updated first, with search over every blog, and a page for every blog with its feed, its posts and its language and blog system.
- **Search**: the search button at the top right of Discover opens a page that browses Explore's tags and finds blogs by name, address or description as you type.
- **Me**: sign in or create an account, change your name or password, delete your account, claim your blog with a DNS record, submit a blog and follow its review, and point the app at another Explore server.
- **Reading**: as on the website, a post first shows a short page naming the blog and the post, which you can turn off, then opens in a browser tab inside the app (Custom Tabs), or in your browser if you prefer. The tab's menu offers the post's share card, following its blog and the blog's page in the app. Links get `utm_source` as on the website; nothing else is added. Posts you have opened are dimmed in the lists; that record stays on the device.
- **Sharing**: a post or a blog shares as a card with the Kite mark and a QR code, to save to Photos or send on. The code opens a post on its share page on Explore and a blog on its page there.
- **Widget**: the latest posts on the Home Screen in three sizes, optionally narrowed to Chinese or English blogs. Tapping a post opens it on the author's site.
- **Languages**: English and Simplified Chinese. Titles and excerpts always stay in the language the author wrote them in.

The app follows the iOS app screen for screen, with Android's own controls: Material 3 components, the system back gesture, Custom Tabs where iOS uses Safari's view. Two iOS features have no Android counterpart and are left out: the mark hidden under the Dynamic Island in screenshots, and the Lock Screen widget.

## Privacy

The app has no analytics and makes no requests to third parties: post thumbnails and blog icons come from the Explore server. Reading needs no account and sends no cookie. Once you sign in, the session token is encrypted with a key in the Android Keystore and sent only to the server it came from; nothing is backed up. Links to posts never go through a redirect.

## Build

You need JDK 21 and the Android SDK with platform 37 (Android Studio brings both). Build and install a debug build, which installs beside a release one as `plus.kite.explore.dev`:

```bash
./gradlew installDebug
```

The app depends only on AndroidX, Jetpack Compose and Kotlin's own libraries; the QR code encoder is the app's own. Everything lives in the `app` module, under `app/src/main/java/plus/kite/explore`:

| Package | What it holds |
|---|---|
| `app` | the app model, routes, tab stacks, deep links and the root screen |
| `data` | the API client, the models, errors and timestamps |
| `core` | formatting, link tagging, preferences and the token store, image loading, Custom Tabs, OPML and QR codes |
| `design` | the theme, avatars, forms and the shared pieces, including the black and white style of main buttons |
| `discover`, `feed`, `blogs`, `search`, `account`, `submit`, `share` | one package per area |
| `widget` | the Latest Posts widget and its settings |

The widget reads from explore.kite.plus; the server chosen in the app applies to the app only.

The client follows the API described in Explore's [docs/design/api.md](https://github.com/kite-plus/explore/blob/main/docs/design/api.md). Strings are worded as the iOS app words them, and its Simplified Chinese is the iOS app's, which uses the website's terms.

## Tests

The unit tests run on the JVM and cover decoding, timestamps, link tagging, avatar colors, notices in the stream, deep links, search, OPML and the QR encoder, whose codes are read back with ZXing:

```bash
./gradlew testDebugUnitTest
```

CI runs them on every push and pull request, along with Android lint (which also fails on a string without Chinese), the comment check (`python3 scripts/check-comments.py app/src`) and a release build. [RELEASE.md](RELEASE.md) describes the checks and how releases are made.

## Links into the app

`explore://` links open a screen directly, and accept the same paths as the website: `explore://blogs/{host}`, `explore://topics/{slug}`, `explore://submissions/{id}`, `explore://notices/{id}`, `explore://submit`, `explore://sign-in`, `explore://recommended`, `explore://following`, `explore://search` and `explore://about`.

## License

Kite for Android is licensed under the [Apache License 2.0](LICENSE).
