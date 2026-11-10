#!/bin/sh
# Catch up to a recovery point without template branches.
# Creates branch catch-up/<point> from the first commit of main (the state generated from the
# template) and replays the commits in catch-up/<point>.patch.gz with git am, keeping every commit and
# its message. Each file is a gzip-compressed mbox made by git format-patch --stdout (see catch-up/README.md).
# The patches are compressed so that agents working in this repository do not read the prepared
# states by accident while they search or read files. This is not access control.
# That first commit must contain the patch: run this in a repository created from the template,
# not in the template repository itself or in a fork.
# Usage: sh scripts/catch-up.sh <point> [--dry-run]
#   points: 1-2, 1-4, 2-1, 2-2, 2-4, 3-1, eval-no-doc, drift-fixture
#   --dry-run replays the commits in a temporary detached worktree at the base commit, reports how
#   many commits would be created and removes the worktree again. No branch is created.
# Exit status: 0 done, 1 cannot apply or uncommitted changes, 3 bad usage or no patch.
set -eu

usage() {
  echo "usage: sh scripts/catch-up.sh <point> [--dry-run]" >&2
  echo "  points: 1-2 1-4 2-1 2-2 2-4 3-1 eval-no-doc drift-fixture" >&2
  exit 3
}
[ $# -ge 1 ] && [ $# -le 2 ] || usage
point=$1
mode=${2:-}
case "$point" in
  ''|-*|*[!A-Za-z0-9-]*) usage ;;
esac
case "$mode" in
  ''|--dry-run) ;;
  *) usage ;;
esac
root=$(git rev-parse --show-toplevel 2>/dev/null) || { echo "run this inside your clone of the workshop repository" >&2; exit 3; }
cd "$root"
patch="catch-up/$point.patch.gz"
if [ ! -f "$patch" ]; then
  echo "no patch for point $point: $patch" >&2
  points=
  for f in catch-up/*.patch.gz; do
    [ -f "$f" ] || continue
    f=${f#catch-up/}
    points="$points ${f%.patch.gz}"
  done
  echo "available points:${points:- none}" >&2
  exit 3
fi
gitdir=$(cd "$(git rev-parse --git-common-dir)" && pwd)
if ! command -v gzip >/dev/null 2>&1; then
  echo "gzip is not available here. run this in the lab (Codespaces) or in Git Bash." >&2
  exit 1
fi

if ! git fetch --quiet origin main; then
  echo "warning: could not fetch origin main; using the local origin/main" >&2
fi
if ! git rev-parse --verify --quiet origin/main >/dev/null; then
  echo "origin/main not found. run this in a clone of your repository on GitHub." >&2
  exit 1
fi
if [ "$(git rev-parse --is-shallow-repository)" = true ]; then
  echo "this clone is shallow, so the first commit of main is unknown. run: git fetch --unshallow origin" >&2
  exit 1
fi
base=$(git rev-list --max-parents=0 origin/main | tail -n 1)
# The patches are made for the first commit of a repository created from the template, which ships them.
if ! git cat-file -e "$base:$patch" 2>/dev/null; then
  echo "the first commit of origin/main ($base) does not contain $patch." >&2
  echo "run this in a repository created with \"Use this template\", not in the template itself or a fork." >&2
  exit 1
fi

# git am needs a committer. Use a fallback identity only when user.email is not configured.
ident=
if [ -z "$(git config --get user.email || true)" ]; then
  ident="-c user.email=catch-up@example.invalid"
  [ -n "$(git config --get user.name || true)" ] || ident="$ident -c user.name=catch-up"
fi

if [ "$mode" = "--dry-run" ]; then
  tmp="$gitdir/catch-up-dry-run-$$"
  log="$tmp.log"
  # shellcheck disable=SC2329 # called by the EXIT trap
  cleanup() {
    git -C "$tmp" am --abort >/dev/null 2>&1 || true
    git worktree remove --force "$tmp" >/dev/null 2>&1 || true
    rm -rf "$tmp" "$log" "$tmp.mbox"
    git worktree prune >/dev/null 2>&1 || true
  }
  trap cleanup EXIT
  trap 'exit 1' HUP INT PIPE TERM
  # Forget trial worktrees whose folders an interrupted earlier run already removed.
  git worktree prune >/dev/null 2>&1 || true
  if ! git worktree add --quiet --detach "$tmp" "$base" >/dev/null 2>&1; then
    echo "dry-run failed: cannot create a temporary worktree at $base" >&2
    exit 1
  fi
  if ! gzip -dc "$root/$patch" >"$tmp.mbox" 2>/dev/null; then
    echo "dry-run failed: cannot decompress $patch" >&2
    exit 1
  fi
  # shellcheck disable=SC2086 # $ident is empty or holds -c options
  if git -C "$tmp" $ident am --quiet "$tmp.mbox" >"$log" 2>&1; then
    count=$(git -C "$tmp" rev-list --count "$base..HEAD")
    echo "dry-run ok: $patch applies to $base; $count commit(s) would be created on catch-up/$point"
    git -C "$tmp" log --reverse --format='  %s' "$base..HEAD"
    exit 0
  fi
  # Show what failed, but not git am's advice on continuing: the trial worktree is removed.
  grep -E '^(error|fatal):|^Patch failed at' "$log" >&2 || true
  echo "dry-run failed: $patch does not apply to $base" >&2
  exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
  echo "uncommitted changes found. commit or stash them first (git stash -u)." >&2
  exit 1
fi
for state in rebase-apply rebase-merge MERGE_HEAD CHERRY_PICK_HEAD REVERT_HEAD; do
  if [ -e "$(git rev-parse --git-path "$state")" ]; then
    echo "a merge, rebase or git am is in progress. finish or abort it first." >&2
    exit 1
  fi
done
branch="catch-up/$point"
if git show-ref --verify --quiet "refs/heads/$branch"; then
  echo "branch $branch already exists. switch to it (git switch $branch) or delete it first (git branch -D $branch)." >&2
  exit 1
fi
if [ -n "$ident" ]; then
  echo "note: git user.email is not set; the catch-up commits use catch-up@example.invalid as the committer." >&2
fi
original=$(git symbolic-ref --quiet --short HEAD || git rev-parse --verify HEAD)
# The patch lives on the current branch; keep a copy before switching to the base commit.
saved="$gitdir/catch-up-$point.patch"
log="$gitdir/catch-up-$point.log"
trap 'rm -f "$saved" "$log"' EXIT
if ! gzip -dc "$patch" >"$saved" 2>/dev/null; then
  echo "cannot decompress $patch" >&2
  exit 1
fi
trap 'exit 1' HUP INT PIPE TERM
if ! git switch --quiet -c "$branch" "$base"; then
  echo "cannot create branch $branch at $base" >&2
  exit 1
fi
# shellcheck disable=SC2086 # $ident is empty or holds -c options
if git $ident am "$saved" >"$log" 2>&1; then
  cat "$log"
  count=$(git rev-list --count "$base..HEAD")
  echo "now on branch $branch ($count commit(s) from $patch). push with: git push -u origin $branch"
  exit 0
fi
grep -E '^Applying:|^(error|fatal):|^Patch failed at' "$log" >&2 || true
echo "could not apply $patch. going back to $original and deleting $branch." >&2
git am --abort >/dev/null 2>&1 || true
if git show-ref --verify --quiet "refs/heads/$original"; then
  git switch --quiet "$original" || true
else
  git switch --quiet --detach "$original" || true
fi
git branch -D "$branch" >/dev/null 2>&1 || true
exit 1