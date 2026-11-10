# Windows version of scripts/catch-up.sh. Same arguments, messages and exit codes.
# Creates branch catch-up/<point> from the first commit of main and replays the commits in
# catch-up/<point>.patch.gz (a gzip-compressed mbox made by git format-patch --stdout) with git am.
# The patches are compressed so that agents working in this repository do not read the prepared
# states by accident while they search or read files. This is not access control.
# That first commit must contain the patch: run this in a repository created from the template,
# not in the template repository itself or in a fork.
# Usage: pwsh scripts/catch-up.ps1 <point> [--dry-run]
#   points: 1-2, 1-4, 2-1, 2-2, 2-4, 3-1, eval-no-doc, drift-fixture
#   --dry-run replays the commits in a temporary detached worktree at the base commit, reports how
#   many commits would be created and removes the worktree again. No branch is created.
#   exit 0: done / 1: cannot apply or uncommitted changes / 3: bad usage or no patch
# Runs on PowerShell 7 (pwsh) and on Windows PowerShell 5.1.
$ErrorActionPreference = 'Continue'
function Fail([int]$code, [string]$message) { [Console]::Error.WriteLine($message); exit $code }
function Expand-Gzip([string]$from, [string]$to) {
  $in = [IO.File]::OpenRead($from)
  try {
    $gz = New-Object IO.Compression.GZipStream($in, [IO.Compression.CompressionMode]::Decompress)
    $out = [IO.File]::Create($to)
    try { $gz.CopyTo($out) } finally { $out.Dispose(); $gz.Dispose() }
  } finally { $in.Dispose() }
}
function Show-Usage { Fail 3 "usage: pwsh scripts/catch-up.ps1 <point> [--dry-run]`n  points: 1-2 1-4 2-1 2-2 2-4 3-1 eval-no-doc drift-fixture" }
if ($args.Count -lt 1 -or $args.Count -gt 2) { Show-Usage }
$Point = [string]$args[0]
$Mode = ''
if ($args.Count -eq 2) { $Mode = [string]$args[1] }
if ($Point -cnotmatch '^[A-Za-z0-9][A-Za-z0-9-]*$') { Show-Usage }
if ($Mode -ne '' -and $Mode -ne '--dry-run') { Show-Usage }
$root = git rev-parse --show-toplevel 2>$null
if ($LASTEXITCODE -ne 0 -or -not $root) { Fail 3 'run this inside your clone of the workshop repository' }
Set-Location -LiteralPath $root
$patch = "catch-up/$Point.patch.gz"
if (-not (Test-Path -LiteralPath $patch -PathType Leaf)) {
  $names = @(Get-ChildItem -LiteralPath 'catch-up' -Filter '*.patch.gz' -ErrorAction SilentlyContinue | ForEach-Object { $_.Name -replace '\.patch\.gz$', '' })
  Fail 3 ("no patch for point ${Point}: $patch`navailable points: " + ($names -join ' '))
}
$gitdir = git rev-parse --git-common-dir
if (-not [IO.Path]::IsPathRooted($gitdir)) { $gitdir = Join-Path $root $gitdir }
$gitdir = [IO.Path]::GetFullPath($gitdir)

git fetch --quiet origin main
if ($LASTEXITCODE -ne 0) { [Console]::Error.WriteLine('warning: could not fetch origin main; using the local origin/main') }
git rev-parse --verify --quiet origin/main | Out-Null
if ($LASTEXITCODE -ne 0) { Fail 1 'origin/main not found. run this in a clone of your repository on GitHub.' }
if ((git rev-parse --is-shallow-repository) -eq 'true') {
  Fail 1 'this clone is shallow, so the first commit of main is unknown. run: git fetch --unshallow origin'
}
$base = git rev-list --max-parents=0 origin/main | Select-Object -Last 1
# The patches are made for the first commit of a repository created from the template, which ships them.
git cat-file -e "${base}:$patch" 2>$null
if ($LASTEXITCODE -ne 0) {
  Fail 1 "the first commit of origin/main ($base) does not contain $patch.`nrun this in a repository created with ""Use this template"", not in the template itself or a fork."
}

# git am needs a committer. Use a fallback identity only when user.email is not configured.
$ident = @()
if (-not (git config --get user.email)) {
  $ident += @('-c', 'user.email=catch-up@example.invalid')
  if (-not (git config --get user.name)) { $ident += @('-c', 'user.name=catch-up') }
}

if ($Mode -eq '--dry-run') {
  $tmp = Join-Path $gitdir "catch-up-dry-run-$PID"
  $mbox = "$tmp.mbox"
  $code = 1
  try {
    # Forget trial worktrees whose folders an interrupted earlier run already removed.
    git worktree prune 2>$null | Out-Null
    git worktree add --quiet --detach $tmp $base 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) {
      [Console]::Error.WriteLine("dry-run failed: cannot create a temporary worktree at $base")
    } else {
      $expanded = $true
      try { Expand-Gzip (Join-Path $root $patch) $mbox } catch { $expanded = $false }
      if (-not $expanded) {
        [Console]::Error.WriteLine("dry-run failed: cannot decompress $patch")
      } else {
      $out = @(git -C $tmp @ident am --quiet $mbox 2>&1 | ForEach-Object { "$_" })
      if ($LASTEXITCODE -eq 0) {
        $count = git -C $tmp rev-list --count "$base..HEAD"
        "dry-run ok: $patch applies to $base; $count commit(s) would be created on catch-up/$Point"
        git -C $tmp log --reverse --format='  %s' "$base..HEAD"
        $code = 0
      } else {
        # Show what failed, but not git am's advice on continuing: the trial worktree is removed.
        $out | Where-Object { $_ -match '^(error|fatal):|^Patch failed at' } | ForEach-Object { [Console]::Error.WriteLine($_) }
        [Console]::Error.WriteLine("dry-run failed: $patch does not apply to $base")
      }
      }
    }
  } finally {
    if (Test-Path -LiteralPath $tmp) {
      git -C $tmp am --abort 2>$null | Out-Null
      git worktree remove --force $tmp 2>$null | Out-Null
      if (Test-Path -LiteralPath $tmp) { Remove-Item -LiteralPath $tmp -Recurse -Force -ErrorAction SilentlyContinue }
    }
    git worktree prune 2>$null | Out-Null
    Remove-Item -LiteralPath $mbox -Force -ErrorAction SilentlyContinue
  }
  exit $code
}

if (git status --porcelain) { Fail 1 'uncommitted changes found. commit or stash them first (git stash -u).' }
foreach ($state in 'rebase-apply', 'rebase-merge', 'MERGE_HEAD', 'CHERRY_PICK_HEAD', 'REVERT_HEAD') {
  if (Test-Path -LiteralPath (git rev-parse --git-path $state)) { Fail 1 'a merge, rebase or git am is in progress. finish or abort it first.' }
}
$branch = "catch-up/$Point"
git show-ref --verify --quiet "refs/heads/$branch"
if ($LASTEXITCODE -eq 0) { Fail 1 "branch $branch already exists. switch to it (git switch $branch) or delete it first (git branch -D $branch)." }
if ($ident.Count -gt 0) { [Console]::Error.WriteLine('note: git user.email is not set; the catch-up commits use catch-up@example.invalid as the committer.') }
$original = git symbolic-ref --quiet --short HEAD
$originalIsBranch = ($LASTEXITCODE -eq 0)
if (-not $originalIsBranch) { $original = git rev-parse --verify HEAD }
# The patch lives on the current branch; keep a copy before switching to the base commit.
$saved = Join-Path $gitdir "catch-up-$Point.patch"
try { Expand-Gzip (Join-Path $root $patch) $saved } catch { Remove-Item -LiteralPath $saved -Force -ErrorAction SilentlyContinue; Fail 1 "cannot decompress $patch" }
try {
  git switch --quiet -c $branch $base
  if ($LASTEXITCODE -ne 0) { Fail 1 "cannot create branch $branch at $base" }
  $out = @(git @ident am $saved 2>&1 | ForEach-Object { "$_" })
  if ($LASTEXITCODE -eq 0) {
    $out
    $count = git rev-list --count "$base..HEAD"
    "now on branch $branch ($count commit(s) from $patch). push with: git push -u origin $branch"
    exit 0
  }
  $out | Where-Object { $_ -match '^Applying:|^(error|fatal):|^Patch failed at' } | ForEach-Object { [Console]::Error.WriteLine($_) }
  [Console]::Error.WriteLine("could not apply $patch. going back to $original and deleting $branch.")
  git am --abort 2>$null | Out-Null
  if ($originalIsBranch) { git switch --quiet $original } else { git switch --quiet --detach $original }
  git branch -D $branch 2>$null | Out-Null
  exit 1
} finally {
  Remove-Item -LiteralPath $saved -Force -ErrorAction SilentlyContinue
}