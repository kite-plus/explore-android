# Changelog

All notable changes to Kite for Android, the Android client of Explore, are recorded in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the version numbers follow [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

[简体中文](CHANGELOG.zh-CN.md)

## [Unreleased]

The first version of Kite for Android, with everything Kite for iOS 0.1.4 offers that Android has a place for.

### Added

- Discover: Latest, Recommended and Following as tabs, as on the website. Swipe between them and the underline follows; tapping Discover again scrolls the stream to the top. Posts run newest first in one list, each saying when it came out, and filter by blog language and tag in a sheet. Recommended asks for each day's best posts in the phone's time zone.
- Notices and ads that Explore publishes show on Latest, between the posts, in the same row as a post with a "Notice" or "Ad" mark. One with a link opens it as a post does; one without opens on a page in the app laid out as on the website.
- Every post opens on the author's own site: first a short page naming the blog and the post, which can be turned off, then a browser tab inside the app, or the browser itself. The tab's menu shares the post as a card, follows its blog and shows the blog's page in the app. Links get only `utm_source`.
- Posts you have opened are dimmed in every list; the record stays on the device and can be turned off, which forgets it.
- Each post shows Explore's latest check of its link, with the details on a tap and a check on the spot for links never checked.
- Blogs: the directory, most recently updated first, with a language filter and search over every blog, and a page for every blog with its posts, Visit, Follow and a menu to copy its feed address, share it, claim it or report it.
- Search for tags and blogs, and a page for every tag.
- Accounts: sign in or create an account, follow blogs, change your name or password, and delete your account. A password an admin reset is changed right after signing in, with a reminder under Me until it is.
- Following: the blogs you follow, with swipe to unfollow, OPML import with the blogs Explore does not list ready to submit, and OPML export.
- Submit a blog with its feed checked on the spot, review what Explore found, and follow the review under My Submissions; claim your blog with a DNS record; report a problem with a blog or a post.
- Posts and blogs share as cards with the Kite mark and a QR code to their pages on Explore: save to Photos, share the image or copy the link.
- A Latest Posts widget in three sizes, optionally narrowed to Chinese or English blogs.
- Kite Plus accounts: on a server that signs in through Kite Plus, Sign In with Kite Plus account comes first and opens the server's sign-in page in a browser tab, where a browser already signed in to Kite Plus goes straight through; Use Another Account picks a different one. There, new accounts come from Kite Plus, so the app offers no sign-up, and accounts made on Explore before still sign in with their password. Me marks an account linked to Kite Plus and offers Delete Kite Plus Account, which opens the Kite Plus account page. On explore.kite.plus, signing in returns to the app through an App Link; other servers return through `explore://`.
- Signed-in requests send the session as a Bearer token. Servers released before Kite Plus sign-in read only the session cookie, so for now the app sends the cookie, and the CSRF token with writes, as well.
- Another Explore server can be chosen under Me; sign-ins are kept per server.
- English and Simplified Chinese.
