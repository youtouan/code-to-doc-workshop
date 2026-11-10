import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { runCrossEntry } from './http-batch-scenario.mjs';

const key = `BT${Date.now().toString(36)}${randomBytes(3).toString('hex')}`.toUpperCase();
const folder = path.resolve('.runtime', 'smoke', key);
const jar = path.resolve('wholesale-batch', 'target', 'wholesale-batch-1.0.0-standalone.jar');
const java = process.env.JAVA_HOME
  ? path.join(process.env.JAVA_HOME, 'bin', process.platform === 'win32' ? 'java.exe' : 'java')
  : 'java';
const dbUrl = process.env.DB_URL
  ?? `jdbc:postgresql://${process.env.PGHOST || '127.0.0.1'}:${process.env.PGPORT || '5432'}/wholesale`;
const dbUser = process.env.DB_USER ?? process.env.PGUSER ?? 'wholesale';
const dbPassword = process.env.DB_PASSWORD ?? process.env.PGPASSWORD ?? 'wholesale-local';
const password = process.env.WHOLESALE_BATCH_PASSWORD ?? 'Demo-batch-2026!';
const results = [];

async function invoke(label, command, args = [], expected = 0, user = 'batch') {
  const authenticationPassword = user === 'admin'
    ? (process.env.WHOLESALE_ADMIN_PASSWORD ?? 'Demo-admin-2026!') : password;
  const invocation = [
    '-Dfile.encoding=UTF-8', '-Duser.timezone=Asia/Tokyo', '-Dorg.slf4j.simpleLogger.defaultLogLevel=warn',
    `-Ddb.url=${dbUrl}`, `-Ddb.username=${dbUser}`, `-Ddb.password=${dbPassword}`,
    '-jar', jar, command, ...args, '--user', user, '--password-env', 'WHOLESALE_SMOKE_PASSWORD'
  ];
  const result = spawnSync(java, invocation, {
    encoding: 'utf8', timeout: 120000, maxBuffer: 8 * 1024 * 1024,
    env: { ...process.env, WHOLESALE_SMOKE_PASSWORD: authenticationPassword }
  });
  assert.equal(result.error, undefined, `Batch process did not execute: ${result.error?.message}`);
  assert.ok(Number.isInteger(result.status), 'A launch error or signal is not a valid batch exit status');
  let log = `${result.stdout}\n${result.stderr}`;
  for (const secret of [dbPassword, authenticationPassword]) if (secret) log = log.replaceAll(secret, '[redacted]');
  await writeFile(path.join(folder, `${label}.log`), log, 'utf8');
  if (expected !== null) {
    assert.equal(result.status, expected, `${label}: unexpected exit ${result.status}; see ${key}/${label}.log`);
  }
  results.push({ label, command, exitCode: result.status });
  return result;
}

function summary(result) {
  const match = result.stdout.match(
    /run_id=(\d+) run_key=(\S+) status=(\S+) total=(\d+) success=(\d+) rejected=(\d+) has_more=(true|false) exit_code=(\d+)/);
  assert.ok(match, 'Batch did not report a persisted run summary');
  return {
    id: match[1], key: match[2], status: match[3],
    total: Number(match[4]), success: Number(match[5]), rejected: Number(match[6]),
    hasMore: match[7] === 'true', exitCode: Number(match[8])
  };
}

function tokyoDate() {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Tokyo', year: 'numeric', month: '2-digit', day: '2-digit'
  }).formatToParts(new Date());
  const part = type => parts.find(value => value.type === type).value;
  return `${part('year')}-${part('month')}-${part('day')}`;
}

async function run() {
  await mkdir(folder, { recursive: true });
  const health = await invoke('health', 'health');
  assert.match(health.stdout, /database=reachable schema=validated batch_tables=reachable/);
  console.log('PASS Standalone batch boot, XML wiring, authentication and PostgreSQL schema');

  const formulaCode = `${key}-F`;
  const productsFile = path.join(folder, 'products.csv');
  const sourceProducts = await readFile(path.join('samples', 'products-valid.csv'), 'utf8');
  await writeFile(productsFile, `${sourceProducts.trimEnd()}\r\n${formulaCode},=1+2,個,STANDARD,1.00,1.00,1,0,0,true,formula export probe\r\n`, 'utf8');
  const productKey = `${key}-products`;
  const imported = summary(await invoke('products', 'import-products', ['--file', productsFile, '--run-key', productKey]));
  assert.equal(imported.status, 'COMPLETED');
  assert.equal(imported.total, 4);
  assert.equal(imported.success, 4);
  assert.equal(imported.rejected, 0);
  const replay = summary(await invoke('products-replay', 'import-products', ['--file', productsFile, '--run-key', productKey]));
  assert.deepEqual(replay, imported, 'An identical completed run must preserve all stored outcomes');
  console.log('PASS Strict CSV product import and persisted identical rerun');

  const rejected = summary(await invoke('products-rejected', 'import-products', [
    '--file', path.resolve('samples', 'products-rejections.csv'), '--run-key', `${key}-rejected`
  ], 3));
  assert.equal(rejected.status, 'PARTIAL');
  assert.equal(rejected.total, 4);
  assert.equal(rejected.success, 1);
  assert.equal(rejected.rejected, 3);
  const rows = await invoke('row-results', 'row-results', ['--run-key', `${key}-rejected`, '--limit', '100']);
  assert.match(rows.stdout, /REJECTED/);
  assert.match(rows.stdout, /SUCCESS/);
  console.log('PASS Row rejections persist independently from successful rows');

  const receiptsFile = path.join(folder, 'receipts.csv');
  const sourceReceipts = await readFile(path.join('samples', 'receipts-valid.csv'), 'utf8');
  const receipts = sourceReceipts.replace(/^csv-receipt-001,/m, `${key}-r1,`)
    .replace(/^csv-receipt-002,/m, `${key}-r2,`);
  assert.equal((receipts.match(new RegExp(`^${key}-r[12],`, 'gm')) || []).length, 2);
  await writeFile(receiptsFile, `${receipts.trimEnd()}\r\n${key}-formula,EAST,${formulaCode},2,1.00,${tokyoDate()},${key}-formula,export verification\r\n`, 'utf8');
  const receiptKey = `${key}-receipts`;
  const receiptRun = summary(await invoke('receipts', 'import-receipts', ['--file', receiptsFile, '--run-key', receiptKey]));
  assert.equal(receiptRun.status, 'COMPLETED');
  assert.equal(receiptRun.total, 3);
  assert.equal(receiptRun.success, 3);
  const before = path.join(folder, 'stock-before.csv');
  const after = path.join(folder, 'stock-after.csv');
  await invoke('stock-before', 'export-stock', ['--output', before, '--limit', '100000']);
  const receiptReplay = summary(await invoke('receipts-replay', 'import-receipts', ['--file', receiptsFile, '--run-key', receiptKey]));
  assert.deepEqual(receiptReplay, receiptRun);
  await invoke('stock-after', 'export-stock', ['--output', after, '--limit', '100000']);
  const exportedBefore = await readFile(before, 'utf8');
  const exportedAfter = await readFile(after, 'utf8');
  assert.equal(exportedAfter, exportedBefore, 'Replayed receipts changed physical or reserved stock');
  assert.ok(exportedAfter.includes("'=1+2"), 'CSV export must escape spreadsheet formulas in text fields');
  console.log('PASS CSV receipts, replay-safe stock quantities and formula-safe export');

  const overwrite = await invoke('refuse-overwrite', 'export-stock', ['--output', after, '--limit', '100000'], null);
  assert.notEqual(overwrite.status, 0);
  assert.match(`${overwrite.stdout}\n${overwrite.stderr}`, /already exists|既に存在|存在する/i);
  assert.equal(await readFile(after, 'utf8'), exportedAfter, 'Refused export overwrote the existing file');
  console.log('PASS Existing export files are preserved on rejection');

  const ordersFile = path.join(folder, 'orders.csv');
  const orderSource = await readFile(path.join('samples', 'orders-valid.csv'), 'utf8');
  const orderData = orderSource.replace(/^CSV-ORDER-/gm, `${key}-ORDER-`)
    .replace(/,CSV-PO-/g, `,${key}-PO-`);
  assert.equal((orderData.match(new RegExp(`^${key}-ORDER-`, 'gm')) || []).length, 3);
  await writeFile(ordersFile, orderData, 'utf8');
  await invoke('order-role-refused', 'import-orders', ['--file', ordersFile, '--run-key', `${key}-order-denied`], 2);
  const orderImport = summary(await invoke('order-import', 'import-orders', [
    '--file', ordersFile, '--run-key', `${key}-orders`
  ], 0, 'admin'));
  assert.equal(orderImport.total, 2, 'Order journal counts logical groups, not physical CSV lines');
  assert.equal(orderImport.success, 2);
  assert.equal(orderImport.rejected, 0);
  const draftBefore = path.join(folder, 'drafts-before.csv');
  const draftAfter = path.join(folder, 'drafts-after.csv');
  await invoke('drafts-before', 'export-orders', ['--output', draftBefore, '--limit', '100000', '--status', 'DRAFT']);
  const externalReplay = summary(await invoke('order-key-replay', 'import-orders', [
    '--file', ordersFile, '--run-key', `${key}-orders-second-run`
  ], 0, 'admin'));
  assert.equal(externalReplay.success, 2);
  await invoke('drafts-after', 'export-orders', ['--output', draftAfter, '--limit', '100000', '--status', 'DRAFT']);
  assert.equal(await readFile(draftAfter, 'utf8'), await readFile(draftBefore, 'utf8'),
    'External-key replay created or altered an existing draft order');
  const rejectedOrders = summary(await invoke('order-rejections', 'import-orders', [
    '--file', path.resolve('samples', 'orders-rejections.csv'), '--run-key', `${key}-order-rejections`
  ], 3, 'admin'));
  assert.equal(rejectedOrders.total, 4);
  assert.equal(rejectedOrders.success, 1);
  assert.equal(rejectedOrders.rejected, 3);
  const fragments = summary(await invoke('order-fragments', 'import-orders', [
    '--file', path.resolve('samples', 'orders-noncontiguous.csv'), '--run-key', `${key}-order-fragments`
  ], 3, 'admin'));
  assert.equal(fragments.total, 3);
  assert.equal(fragments.success, 1);
  assert.equal(fragments.rejected, 2);
  console.log('PASS Grouped draft-order imports, permission checks, durable external keys and fragment rejection');

  await invoke('orders-export', 'export-orders', ['--output', path.join(folder, 'orders-export.csv'), '--limit', '100000']);
  await invoke('invoices-export', 'export-invoices', ['--output', path.join(folder, 'invoices.csv'), '--limit', '100000']);
  await invoke('shipment-lines-export', 'export-shipments', [
    '--output', path.join(folder, 'shipment-lines.csv'), '--limit', '100000', '--status', 'CONFIRMED'
  ]);
  await invoke('receipt-export', 'export-receipts', ['--output', path.join(folder, 'receipts-export.csv'), '--limit', '100000']);
  await invoke('order-lines-export', 'export-order-lines', [
    '--output', path.join(folder, 'order-lines.csv'), '--limit', '100000', '--status', 'DRAFT'
  ]);
  if (process.env.SMOKE_BASE_URL) {
    const crossEntry = await runCrossEntry(invoke, process.env.SMOKE_BASE_URL, key);
    await writeFile(path.join(folder, 'cross-entry.json'), JSON.stringify(crossEntry, null, 2), 'utf8');
  }
  await writeFile(path.join(folder, 'results.json'), JSON.stringify({ key, at: new Date().toISOString(), results }, null, 2), 'utf8');
  console.log(`Batch smoke complete: ${results.length} actual process invocations; ${key}.`);
}

run().catch(error => {
  console.error(error.message);
  process.exitCode = 1;
});
