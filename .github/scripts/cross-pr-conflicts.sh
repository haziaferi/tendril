#!/usr/bin/env bash
#
# Test-merge this PR's head against every other open PR's head and report file-level
# conflicts. Advisory: it never fails the build.
#
# Why this exists: GitHub computes a PR's `mergeable` / `CLEAN` status against the base
# branch only. It never compares two open PRs to each other, so two branches can both
# report CLEAN while conflicting badly — and nothing says so until someone merges the
# second one and hits it by hand. PR #1 and PR #3 in this repo both read CLEAN and
# conflicted in 18 files.
#
# `git merge-tree --write-tree` is the right tool: it computes the merge in the object
# database, writes no refs and touches no working tree, so it is safe to run in CI and
# safe to run repeatedly.
#
# Runnable locally, which is how it was verified:
#   MINE=my-branch OTHERS="other-a other-b" bash .github/scripts/cross-pr-conflicts.sh
set -uo pipefail

# No apostrophe in this message: bash re-parses the word inside ${var:?word}, so a bare
# quote here opens one that never closes and the whole file fails to parse.
: "${MINE:?set MINE to the head ref of this PR}"
SUMMARY="${GITHUB_STEP_SUMMARY:-/dev/stdout}"

# OTHERS may be pre-set for local testing; in CI it comes from the open PR list.
if [ -z "${OTHERS:-}" ]; then
  OTHERS="$(gh pr list --state open --json headRefName -q '.[].headRefName' | grep -vx "$MINE" || true)"
fi

if [ -z "${OTHERS// /}" ]; then
  echo "No other open PRs to compare against." >> "$SUMMARY"
  exit 0
fi

git fetch origin $OTHERS --quiet 2>/dev/null || true

echo "## Cross-PR conflicts (advisory)" >> "$SUMMARY"
echo "" >> "$SUMMARY"

any=0
for other in $OTHERS; do
  [ "$other" = "$MINE" ] && continue

  # A branch that is an ancestor of this one cannot conflict with it, and reporting the
  # pair would be noise — that is a containment relationship, not a collision.
  if git merge-base --is-ancestor "origin/$other" "origin/$MINE" 2>/dev/null; then
    echo "- \`$other\` is already contained in this branch." >> "$SUMMARY"
    continue
  fi

  conflicts="$(git merge-tree --write-tree "origin/$MINE" "origin/$other" 2>/dev/null |
    grep '^CONFLICT' |
    sed -E 's/^CONFLICT \([^)]*\): (Merge conflict in )?//' |
    sed 's/ deleted in .*//' |
    sort -u || true)"

  if [ -z "$conflicts" ]; then
    echo "- No conflicts with \`$other\`." >> "$SUMMARY"
    continue
  fi

  any=1
  count="$(printf '%s\n' "$conflicts" | wc -l | tr -d ' ')"
  {
    echo ""
    echo "<details><summary><b>${count} conflicting file(s) with <code>${other}</code></b></summary>"
    echo ""
    printf '%s\n' "$conflicts" | sed 's/^/- `/; s/$/`/'
    echo ""
    echo "</details>"
  } >> "$SUMMARY"
  echo "::warning::Conflicts with open PR branch ${other} in ${count} file(s) — whichever merges second will have to resolve them"
done

if [ "$any" -eq 0 ]; then
  echo "" >> "$SUMMARY"
  echo "No conflicts with any other open PR." >> "$SUMMARY"
fi

exit 0
