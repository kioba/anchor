#!/usr/bin/env bash
set -euo pipefail

# Generates docs/llms-full.txt from the source documentation files.
# MkDocs macros ({{ version }}, {{ group_id }}) are resolved to their
# actual values from mkdocs.yml so the output is a standalone file.

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUTPUT="$REPO_ROOT/docs/llms-full.txt"

# Read version and group_id from mkdocs.yml (the source of truth for docs)
VERSION=$(grep -oE 'version: "[0-9]+\.[0-9]+\.[0-9]+"' "$REPO_ROOT/mkdocs.yml" | grep -oE '[0-9]+\.[0-9]+\.[0-9]+')
GROUP_ID=$(grep -oE 'group_id: "[^"]+"' "$REPO_ROOT/mkdocs.yml" | grep -oE '"[^"]*"' | tr -d '"')

if [ -z "$VERSION" ] || [ -z "$GROUP_ID" ]; then
  echo "Error: Could not read version or group_id from mkdocs.yml" >&2
  exit 1
fi

# Helper: read a docs file, strip MkDocs-specific syntax, resolve macros
process_doc() {
  local file="$1"
  sed \
    -e "s/{{ version }}/$VERSION/g" \
    -e "s/{{ group_id }}/$GROUP_ID/g" \
    -e '/^\[\!\[/d' \
    -e '/^!!! /d' \
    -e '/^    \*\*/d' \
    -e 's/](concepts\.md)/](https:\/\/kioba.github.io\/anchor\/concepts\/)/g' \
    -e 's/](api\.md)/](https:\/\/kioba.github.io\/anchor\/api\/)/g' \
    -e 's/](examples\.md)/](https:\/\/kioba.github.io\/anchor\/examples\/)/g' \
    -e 's/](compose\.md)/](https:\/\/kioba.github.io\/anchor\/compose\/)/g' \
    -e 's/](testing\.md)/](https:\/\/kioba.github.io\/anchor\/testing\/)/g' \
    "$file"
}

cat > "$OUTPUT" << HEADER
# Anchor — Complete Documentation

> A lightweight, type-safe state management architecture for Kotlin Multiplatform with Jetpack Compose integration.

- Repository: https://github.com/kioba/anchor
- Published version: $VERSION
- Group ID: $GROUP_ID
- Artifacts: anchor, anchor-compose, anchor-test
- Platforms: Android, iOS (iosX64, iosArm64, iosSimulatorArm64), Desktop (JVM)

---
HEADER

# Concatenate docs in navigation order
DOCS=(
  "docs/index.md"
  "docs/concepts.md"
  "docs/compose.md"
  "docs/testing.md"
  "docs/api.md"
  "docs/examples.md"
)

for doc in "${DOCS[@]}"; do
  filepath="$REPO_ROOT/$doc"
  if [ ! -f "$filepath" ]; then
    echo "Warning: $doc not found, skipping" >&2
    continue
  fi

  echo "" >> "$OUTPUT"
  process_doc "$filepath" >> "$OUTPUT"
  echo "" >> "$OUTPUT"
  echo "---" >> "$OUTPUT"
done

# Remove trailing separator
sed -i '' -e '$ { /^---$/d; }' "$OUTPUT" 2>/dev/null || sed -i -e '$ { /^---$/d; }' "$OUTPUT"

echo "Generated $OUTPUT (version: $VERSION, group_id: $GROUP_ID)"
