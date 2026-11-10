#!/bin/sh
# Reset the lab to a clean state: stop containers, drop database volumes,
# start the web app again, and wait for the login page.
# Source files and git history are not touched.
set -eu
cd "$(git rev-parse --show-toplevel)"
docker compose --profile batch down --volumes --remove-orphans
rm -rf .runtime/exports
mkdir -p .runtime/exports
docker compose up --detach web
sh scripts/lab/wait-for-app.sh
echo "lab reset: done"
