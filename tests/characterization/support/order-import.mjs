// Helper for characterization tests of the order CSV import (Node.js 22 or later, no dependencies).
// It runs scripts/observe-import.sh --json, the same wrapper that 1-3 uses, so that tests and observations
// launch and reset the legacy batch in exactly the same way: a fresh database is created from
// database/*.sql, then import-orders runs once in the compose "batch" container. Needs Docker (the lab).
import { spawnSync } from 'node:child_process';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..', '..', '..');

// Runs the import of one CSV (a path from the repository root) and returns what the system reported:
// { exitCode, stdout, stderr, run, rows, salesOrders, claims, ... } (see scripts/observe-import.sh).
// `database` keeps the test data apart from the 1-3 observation database (wholesale_observe).
export function importOrders(csv, { database = 'wholesale_characterization' } = {}) {
  const run = spawnSync('sh', ['scripts/observe-import.sh', '--json', csv], {
    cwd: root, encoding: 'utf8', timeout: 15 * 60 * 1000, maxBuffer: 16 * 1024 * 1024,
    env: { ...process.env, OBSERVE_DB: database }
  });
  if (run.error) throw run.error;
  if (run.status !== 0) {
    throw new Error(`scripts/observe-import.sh failed (exit ${run.status}). Run the tests in the lab, where Docker is available.\n${run.stderr}`);
  }
  return JSON.parse(run.stdout);
}

// The stored row result of one order group, found by its external_key.
export function rowFor(result, externalKey) {
  const rows = result.rows.filter(row => row.externalKey === externalKey);
  if (rows.length !== 1) throw new Error(`expected one row result for ${externalKey}, found ${rows.length}`);
  return rows[0];
}

// Number of orders that the import created for one external_key (order import claims).
export function ordersCreatedFor(result, externalKey) {
  return result.claims.filter(claim => claim.externalKey === externalKey).length;
}
