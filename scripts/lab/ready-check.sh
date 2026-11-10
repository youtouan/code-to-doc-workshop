#!/bin/sh
# Pre-event readiness check. Prints one line per item and a final verdict.
# Exit 0: ready / 1: something is missing / 3: could not check.
set -u
cd "$(git rev-parse --show-toplevel 2>/dev/null)" || { echo "NG  not inside a git repository"; exit 3; }
fail=0
ok()  { echo "OK  $1"; }
ng()  { echo "NG  $1"; fail=1; }

branch=$(git symbolic-ref --short refs/remotes/origin/HEAD 2>/dev/null | sed 's#^origin/##')
[ "$branch" = "main" ] && ok "default branch is main" || ng "default branch is '${branch:-unknown}', expected main"
git ls-remote --exit-code origin HEAD >/dev/null 2>&1 && ok "can reach origin" || ng "cannot reach origin (network or permission)"
command -v gh >/dev/null 2>&1 && gh auth status >/dev/null 2>&1 && ok "gh is signed in" || ng "gh is not signed in (gh auth login)"
java -version 2>&1 | grep -q '"1\.8' && ok "JDK 8" || ng "JDK 8 not found"
command -v mvn >/dev/null 2>&1 && ok "Maven" || ng "Maven not found"
command -v node >/dev/null 2>&1 && ok "Node.js $(node --version)" || ng "Node.js not found"
docker info >/dev/null 2>&1 && ok "Docker" || ng "Docker is not running"
sh scripts/lab/wait-for-app.sh http://127.0.0.1:8080/wholesale 60 >/dev/null 2>&1 && ok "app answers on /wholesale/login.do" || ng "app does not answer (sh scripts/lab/reset-lab.sh)"
[ -f .github/agents/evidence-reviewer.agent.md ] && ok "evidence-reviewer agent" || ng "evidence-reviewer agent missing"

if [ "$fail" -eq 0 ]; then
  echo "準備完了（ready）"
  exit 0
fi
echo "準備が終わっていません。NG の行を直すか、当日 12:30 の Readiness Clinic にお越しください。"
exit 1
