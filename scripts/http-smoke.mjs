import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { HttpClient, assertPage, findForm, links, withFields } from './http-client.mjs';
import { runBusinessFlow } from './http-business-smoke.mjs';
import { runPayablesFlow } from './http-payables-smoke.mjs';
import { runExtensionAcceptanceFlow } from './http-extension-smoke.mjs';
import { runApBoundaries } from './http-ap-boundaries.mjs';

const baseUrl = process.argv[2] || 'http://127.0.0.1:18080/wholesale';
const results = [];
const failures = [];

async function check(name, action) {
  try {
    await action();
    results.push({ name, result: 'PASS' });
    console.log(`PASS ${name}`);
  } catch (error) {
    results.push({ name, result: 'FAIL', error: error.message });
    failures.push(error);
    console.error(`FAIL ${name}: ${error.message}`);
  }
}

async function run() {
  const anonymous = new HttpClient(baseUrl);
  const login = await anonymous.get('login.do');
  assertPage(login);
  await check('Unauthenticated orders redirect to login', async () => {
    const page = await anonymous.request('orders.do', { follow: false });
    assert.ok([302, 303].includes(page.status));
    assert.ok(new URL(page.headers.get('location'), page.url).pathname.endsWith('/login.do'));
  });
  await check('Login rejects missing CSRF token', async () => {
    const form = findForm(login, 'login');
    const fields = withFields(form, { login: 'admin', password: 'Demo-admin-2026!', csrfToken: null });
    const denied = await anonymous.request(new URL(form.action, login.url), { method: 'POST', fields, follow: false });
    assert.equal(denied.status, 403);
    const protectedPage = await anonymous.request('orders.do', { follow: false });
    assert.ok([302, 303].includes(protectedPage.status), 'Rejected CSRF request must not authenticate');
  });
  await check('Server sends browser protection headers', async () => {
    assert.equal(login.headers.get('x-content-type-options'), 'nosniff');
    assert.equal(login.headers.get('x-frame-options'), 'DENY');
    assert.match(login.headers.get('content-security-policy') || '', /frame-ancestors 'none'/);
    assert.match(login.headers.get('cache-control') || '', /no-store/);
  });

  const admin = new HttpClient(baseUrl);
  const dashboard = await admin.login('admin');
  console.log('PASS Real demo authentication and session rotation');
  await check('Unmapped Struts actions return a safe 404', async () => {
    const missing = await admin.get('missing-route-for-smoke.do');
    assert.equal(missing.status, 404);
    assert.ok(!missing.body.includes('InvalidPathException'), 'Framework exceptions must not leak to the page');
  });
  const pages = new Map([['dashboard.do', dashboard]]);
  for (const path of [
    'customers.do', 'products.do', 'warehouses.do', 'orders.do', 'stock.do', 'receipts.do',
    'shipments.do', 'returns.do', 'invoices.do', 'payments.do', 'statements.do',
    'transfers.do', 'adjustments.do', 'counts.do', 'suppliers.do', 'purchases.do',
    'replenishment.do', 'users.do', 'audit.do', 'stockReports.do',
    'apInvoices.do', 'apCredits.do', 'apPayments.do', 'apReports.do'
  ]) {
    await check(`Render ${path}`, async () => {
      const page = await admin.get(path);
      assertPage(page);
      pages.set(path, page);
    });
  }
  await check('Customer-specific price page uses persisted master data', async () => {
    assertPage(await admin.get('prices.do?customerId=1'));
  });
  const expanded = new Set();
  for (const page of pages.values()) {
    for (const link of links(page.body, page.url)) {
      if (link.url.origin !== new URL(baseUrl).origin || !link.url.pathname.endsWith('.do')) continue;
      if (!['new', 'edit', 'detail', 'view'].includes(link.url.searchParams.get('op'))) continue;
      const identity = `${link.url.pathname}?op=${link.url.searchParams.get('op')}`;
      if (expanded.has(identity)) continue;
      expanded.add(identity);
      await check(`Render linked ${identity}`, async () => assertPage(await admin.get(link.url)));
    }
  }
  await check('Logout rejects missing CSRF without clearing authentication', async () => {
    const denied = await admin.request('logout.do', {
      method: 'POST', fields: new URLSearchParams({ op: 'logout' }), follow: false
    });
    assert.equal(denied.status, 403);
    assertPage(await admin.get('dashboard.do'));
  });
  await check('Japanese common header and footer remain UTF-8', async () => {
    assert.ok(login.body.includes('つばめ卸'), 'Common JSP fragment text is not correctly decoded');
    assert.ok(!/[\u00c2\u00c3\u00e3][\u0080-\u00bf]/.test(login.body),
      'Detected UTF-8 bytes incorrectly interpreted as single-byte text');
  });
  await check('State-changing business workflows use the deployed WAR and shared services', async () => {
    await runBusinessFlow(baseUrl);
  });
  await check('Supplier invoices use actual receipts, variance approval and settlement ledgers', async () => {
    await runPayablesFlow(baseUrl);
  });
  await check('AP row limits and inactive historical selection work at their actual boundaries', async () => {
    await runApBoundaries(baseUrl);
  });
  await check('Extension quotation, amendment, dispatch delivery and operations reports use actual HTTP endpoints', async () => {
    await runExtensionAcceptanceFlow(baseUrl);
  });

  await mkdir('.runtime/smoke', { recursive: true });
  await writeFile('.runtime/smoke/http-results.json',
    JSON.stringify({ baseUrl, at: new Date().toISOString(), results }, null, 2), 'utf8');
  if (failures.length) throw new AggregateError(failures, `${failures.length} HTTP smoke checks failed`);
  console.log(`HTTP smoke complete: ${results.length} checks passed.`);
}

run().catch(error => {
  console.error(error.message);
  process.exitCode = 1;
});
