#!/bin/sh
set -eu
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
: "${PGHOST:=127.0.0.1}" "${PGPORT:=5432}" "${PGUSER:=wholesale}" "${PGDATABASE:=wholesale}"
export PGHOST PGPORT PGUSER PGDATABASE
export PGCLIENTENCODING=UTF8
PSQL=${PSQL:-psql}
existing=$("$PSQL" -X -v ON_ERROR_STOP=1 -Atc "select count(*) from information_schema.tables where table_schema='public'")
if [ "$existing" != "0" ]; then
    echo "Database public schema is not empty. Use a new dedicated database; no data is reset." >&2
    exit 1
fi
set -- -X -v ON_ERROR_STOP=1 --single-transaction
for file in "$ROOT"/database/[0-9][0-9][0-9]-*.sql; do
    if [ "${SCHEMA_ONLY:-false}" = true ]; then
        case "$(basename "$file")" in
            08[0-9]-*|09[0-9]-*) continue ;;
        esac
    fi
    set -- "$@" -f "$file"
done
"$PSQL" "$@"
printf 'Initialized %s.\n' "$PGDATABASE"
