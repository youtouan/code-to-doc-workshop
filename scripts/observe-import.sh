#!/bin/sh
# 1-3 runtime observation: run the legacy order import once and print what the system reported.
#
# Usage: sh scripts/observe-import.sh <orders.csv> [--json]
#   example: sh scripts/observe-import.sh samples/orders-rejections.csv
#   --json prints one JSON object instead of text (tests/characterization/support/order-import.mjs uses it).
#
# What is reset on every run, so that a re-run shows the same observation:
#   the PostgreSQL database "wholesale_observe" (or $OBSERVE_DB) in the compose "database" container is
#   dropped and re-created, and database/[0-9][0-9][0-9]-*.sql (schema and demo data) is loaded into it in
#   the same order as scripts/initialize-database.sh. Nothing else is reset: the app database "wholesale",
#   its volume and the running web app are not touched. To reset the whole lab: sh scripts/lab/reset-lab.sh
# What is run (connected to that database; the plain "batch" account may not import orders):
#   docker compose --profile batch run --rm batch import-orders --file <csv> --run-key observe-<file name>
#     --user admin --password-env WHOLESALE_ADMIN_PASSWORD
# What is printed: the import's exit code, stdout and stderr, then the stored row results of the run and
#   the orders that exist afterwards, read from the same database. No interpretation is added.
#   Logging: like scripts/batch-smoke.mjs, Hibernate logs at WARN (-Dorg.slf4j.simpleLogger.defaultLogLevel=warn).
#   The text output also leaves out Spring's INFO log records and blank lines; .runtime/observe/<database>/
#   stderr.txt keeps them. The JVM notice "Picked up JAVA_TOOL_OPTIONS: ..." is dropped everywhere (it
#   repeats the database password).
# Duration: about 20 seconds per run when the batch image is up to date (onCreateCommand builds it in the lab;
#   each run rebuilds it first, which takes a few seconds from the cache or a few minutes after a code change):
#   about 3 s to re-create the database and 14 s for the batch JVM; a busy machine may need up to a minute.
# Exit status: 0 observation printed (whatever the import returned), 1 docker or database failure, 3 bad usage.
set -eu

usage() { echo "usage: sh scripts/observe-import.sh <orders.csv> [--json]" >&2; exit 3; }
json=false
csv=
for arg in "$@"; do
  case "$arg" in
    --json) json=true ;;
    -*) usage ;;
    *) [ -z "$csv" ] || usage; csv=$arg ;;
  esac
done
[ -n "$csv" ] || usage
case "$csv" in
  /*) input=$csv ;;
  *) input="$(pwd)/$csv" ;;
esac
[ -f "$input" ] || { echo "no such file: $csv" >&2; exit 3; }
db=${OBSERVE_DB:-wholesale_observe}
case "$db" in
  ''|[!a-z_]*|*[!a-z0-9_]*) echo "OBSERVE_DB must be a lower-case SQL identifier: $db" >&2; exit 3 ;;
esac
db_user=${DB_USER:-wholesale}
db_password=${DB_PASSWORD:-wholesale-local}
admin_password=${WHOLESALE_ADMIN_PASSWORD:-Demo-admin-2026!}
base=$(basename "$input")
key="observe-$(basename "$input" .csv | sed 's/[^A-Za-z0-9_.:-]/-/g' | cut -c1-100)"

cd "$(git rev-parse --show-toplevel)"
if ! docker compose version >/dev/null 2>&1; then
  echo "docker compose is not available here. Run this in the lab (Codespaces)." >&2
  exit 1
fi
if docker compose --progress quiet version >/dev/null 2>&1; then
  dc() { docker compose --progress quiet "$@"; }
else
  dc() { docker compose "$@"; }
fi
psql_in() {
  target=$1
  shift
  dc exec -T -e 'PGOPTIONS=-c client_min_messages=warning' -e PGCLIENTENCODING=UTF8 database \
    psql -X -q -v ON_ERROR_STOP=1 -U "$db_user" -d "$target" "$@"
}

# Reset: a fresh observation database with the schema and the demo data.
dc up --detach --wait database >/dev/null
psql_in postgres -c "DROP DATABASE IF EXISTS $db WITH (FORCE)" -c "CREATE DATABASE $db"
# The script in single quotes runs inside the database container; $1 and $2 are its arguments.
# shellcheck disable=SC2016
dc exec -T -e PGCLIENTENCODING=UTF8 database sh -c '
  db=$1; user=$2; set --
  for file in /docker-entrypoint-initdb.d/[0-9][0-9][0-9]-*.sql; do set -- "$@" -f "$file"; done
  exec psql -X -q -v ON_ERROR_STOP=1 --single-transaction -U "$user" -d "$db" "$@"' sh "$db" "$db_user" >/dev/null
before=$(psql_in "$db" -At -c "select count(*) from sales_order")

# Run the import once.
outdir=".runtime/observe/$db"
mkdir -p "$outdir"
# Build the batch image from the sources you have checked out, so that the import runs that code
# (for example after switching to a catch-up branch). Docker reuses its cache when nothing changed;
# after a code change the build takes a few minutes.
if ! dc --profile batch build batch >"$outdir/build.log" 2>&1; then
  echo "could not build the batch image; see $outdir/build.log" >&2
  exit 1
fi
set +e
dc --profile batch run --rm -T \
  -v "$input:/data/input/$base:ro" \
  -e "JAVA_TOOL_OPTIONS=-Ddb.url=jdbc:postgresql://database:5432/$db -Ddb.username=$db_user -Ddb.password=$db_password -Dorg.slf4j.simpleLogger.defaultLogLevel=warn" \
  -e "WHOLESALE_ADMIN_PASSWORD=$admin_password" \
  batch import-orders --file "/data/input/$base" --run-key "$key" --user admin --password-env WHOLESALE_ADMIN_PASSWORD \
  >"$outdir/stdout.txt" 2>"$outdir/stderr.raw"
code=$?
set -e
grep -v '^Picked up JAVA_TOOL_OPTIONS:' "$outdir/stderr.raw" >"$outdir/stderr.txt" || true
rm -f "$outdir/stderr.raw"
command="import-orders --file /data/input/$base --run-key $key --user admin --password-env WHOLESALE_ADMIN_PASSWORD"

if [ "$json" = true ]; then
  psql_in "$db" -At -v input="$csv" -v key="$key" -v code="$code" -v before="$before" -v command="$command" \
    -v out="$(cat "$outdir/stdout.txt")" -v err="$(cat "$outdir/stderr.txt")" -f - <<'SQL'
select json_build_object(
  'input', :'input',
  'database', current_database(),
  'command', :'command',
  'runKey', :'key',
  'exitCode', :code,
  'stdout', coalesce(string_to_array(nullif(:'out', ''), E'\n'), '{}'),
  'stderr', coalesce(string_to_array(nullif(:'err', ''), E'\n'), '{}'),
  'run', (select json_build_object('status', status, 'total', total_rows, 'success', success_rows,
            'rejected', rejected_rows, 'hasMore', has_more, 'exitCode', exit_code, 'message', message)
          from batch_run where run_key = :'key'),
  'rows', coalesce((select json_agg(json_build_object(
            'rowNumber', r.row_number, 'sourceLine', r.source_line, 'result', r.result, 'code', r.code,
            'message', r.message,
            'externalKey', substring(r.message from 'external_key=([^;]*)'),
            'sourceRows', substring(r.message from 'source_rows=([0-9.]+)'),
            'entityType', r.entity_type, 'entityId', r.entity_id, 'entityReference', r.entity_reference,
            'ordersForExternalKey', (select count(*) from batch_order_import c
                                     where c.external_key = substring(r.message from 'external_key=([^;]*)')))
            order by r.row_number)
          from batch_row r join batch_run b on b.id = r.run_id where b.run_key = :'key'), '[]'::json),
  'salesOrders', json_build_object('before', :before, 'after', (select count(*) from sales_order)),
  'claims', coalesce((select json_agg(json_build_object('externalKey', c.external_key, 'orderId', c.order_id,
            'orderNumber', o.number) order by c.id)
          from batch_order_import c join sales_order o on o.id = c.order_id), '[]'::json));
SQL
  exit 0
fi

echo "input: $csv"
echo "reset: database \"$db\" re-created from database/*.sql (the app database \"wholesale\" was not touched)"
echo "command: $command"
echo "exit_code: $code"
echo "stdout:"
sed 's/^/  /' "$outdir/stdout.txt"
echo "stderr (without Spring INFO log records and blank lines):"
awk '
  NF == 0 { next }
  /^[A-Z][a-z][a-z] [0-9][0-9]?, [0-9][0-9][0-9][0-9] [0-9][0-9]?:[0-9][0-9]:[0-9][0-9] [AP]M / {
    if (held != "") print held
    held = $0; next
  }
  held != "" { if ($0 ~ /^INFO: /) { held = ""; next } print held; held = "" }
  { print }
  END { if (held != "") print held }' "$outdir/stderr.txt" | sed 's/^/  /'
echo "row results (stored by the batch; the row-results command shows the same rows):"
psql_in "$db" -At -v key="$key" -f - <<'SQL'
select format(E'  row_number=%s source_line=%s result=%s code=%s\n    external_key: %s\n    source_rows: %s\n    message: %s\n    orders created for this external_key: %s%s',
  r.row_number, r.source_line, r.result, r.code,
  coalesce(substring(r.message from 'external_key=([^;]*)'), '-'),
  coalesce(substring(r.message from 'source_rows=([0-9.]+)'), '-'),
  r.message,
  (select count(*) from batch_order_import c where c.external_key = substring(r.message from 'external_key=([^;]*)')),
  case when r.entity_type is null then ''
       else format(E'\n    entity: %s id=%s reference=%s', r.entity_type, r.entity_id, r.entity_reference) end)
from batch_row r join batch_run b on b.id = r.run_id
where b.run_key = :'key'
order by r.row_number;
SQL
psql_in "$db" -At -v before="$before" -f - <<'SQL'
select format('orders: sales_order rows before=%s after=%s', :before, count(*)) from sales_order;
SQL
