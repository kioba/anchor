#!/usr/bin/env bash
set -euo pipefail

# Prints the body of the "## [<version>]" section of CHANGELOG.md (Keep a
# Changelog format) for use as GitHub release notes. Exits non-zero when the
# section is missing or empty so release workflows fail before tagging.

VERSION="${1:?usage: release-notes.sh <version> [changelog-file]}"
REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FILE="${2:-$REPO_ROOT/CHANGELOG.md}"

if [ ! -f "$FILE" ]; then
  echo "Error: $FILE not found" >&2
  exit 1
fi

BODY=$(awk -v v="$VERSION" '
  index($0, "## [" v "]") == 1 { found = 1; next }
  found && /^## / { exit }
  found { print }
' "$FILE")

if [ -z "$(printf '%s' "$BODY" | tr -d '[:space:]')" ]; then
  echo "Error: no non-empty '## [$VERSION]' section in $FILE" >&2
  exit 1
fi

printf '%s\n' "$BODY"
