#!/bin/sh
# Builds the GitHub Release body for a tag from the changelogs, as Explore
# does: the Chinese section is the body and the English one is a link to
# its own section of CHANGELOG.md. A tag with no section in both files
# fails here, so a release never goes out with empty notes.
#
# Usage: scripts/release-notes.sh v0.1.1 > notes.md
# PREVIOUS_TAG, when set, ends the notes with a compare link.
set -eu
cd "$(dirname "$0")/.."

tag=${1:-}
case "$tag" in
    v[0-9]*.[0-9]*.[0-9]*) ;;
    *) echo "usage: scripts/release-notes.sh v<major>.<minor>.<patch>" >&2 && exit 2 ;;
esac
version=${tag#v}
repository=https://github.com/kite-plus/explore-android

# The body of the version's section, without its heading or the blank
# lines around it.
section() {
    awk -v heading="## [$version]" '
        index($0, heading) == 1 { found = 1; next }
        found && /^## / { exit }
        found { lines[++n] = $0 }
        END {
            first = 1
            while (first <= n && lines[first] ~ /^[ \t\r]*$/) first++
            last = n
            while (last >= first && lines[last] ~ /^[ \t\r]*$/) last--
            for (i = first; i <= last; i++) print lines[i]
        }
    ' "$1"
}

zh=$(section CHANGELOG.zh-CN.md)
en=$(section CHANGELOG.md)
if [ -z "$zh" ] || [ -z "$en" ]; then
    echo "CHANGELOG.md and CHANGELOG.zh-CN.md both need a section for $version" >&2
    exit 1
fi

# GitHub's anchor for the heading: lower case, punctuation dropped, spaces
# made hyphens, so "[0.1.0] - 2026-10-04" becomes "010---2026-10-04".
anchor=$(grep -F "## [$version]" CHANGELOG.md | head -n 1 | sed 's/^## //' |
    tr '[:upper:]' '[:lower:]' | sed 's/[^a-z0-9 _-]//g' | tr ' ' '-')

if [ -n "${PREVIOUS_TAG:-}" ]; then
    footer="**完整变更**：$repository/compare/$PREVIOUS_TAG...$tag"
else
    footer="**更新日志**：$repository/blob/main/CHANGELOG.zh-CN.md"
fi

printf '[English](%s/blob/main/CHANGELOG.md#%s)\n\n%s\n\n---\n\n%s\n' "$repository" "$anchor" "$zh" "$footer"
