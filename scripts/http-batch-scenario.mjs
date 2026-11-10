import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { HttpClient, assertPage, decodeHtml, links } from './http-client.mjs';

export async function runCrossEntry(invoke, baseUrl, key) {
  const prior = JSON.parse(await readFile(path.join('.runtime', 'smoke', 'http-fixture.json'), 'utf8'));
  const admin = new HttpClient(baseUrl);
  const sales = new HttpClient(baseUrl);
  const manager = new HttpClient(baseUrl);
  const warehouse = new HttpClient(baseUrl);
  await admin.login('admin');
  await sales.login('sales');
  await manager.login('manager');
  await warehouse.login('warehouse');

  let page = await admin.submit(await admin.get('customers.do?op=new'), 'save', {
    code: `${key}-C`, name: `バッチ連携確認 ${key}`, active: 'true', onHold: 'false',
    creditLimit: '1000000.00', closingDay: '31', paymentTermDays: '30', taxRounding: 'DOWN',
    postalCode: '000-0000', address: '架空県連携市1-1', telephone: '000-000-0000', notes: 'HTTP to CLI integration'
  });
  assertPage(page);
  const customerId = page.url.searchParams.get('id');
  assert.match(customerId || '', /^[1-9][0-9]*$/);
  const orderDate = new Date(`${prior.periodEnd}T00:00:00Z`);
  orderDate.setUTCDate(orderDate.getUTCDate() - 2);
  page = await sales.submit(await sales.get('orders.do?op=new'), 'save', {
    customerId, warehouseId: '21', orderDate: orderDate.toISOString().slice(0, 10),
    requestedDate: prior.periodEnd, productId: [prior.productId], quantity: ['3'],
    priceOverride: [''], priceReason: [''], externalReference: `${key}-CLI`,
    deliveryAddress: '架空県連携市1-1', notes: 'Allocation and billing executed by the standalone CLI'
  });
  assertPage(page);
  const orderId = page.url.searchParams.get('id');
  assert.match(orderId || '', /^[1-9][0-9]*$/);
  assertPage(await sales.submit(page, 'submit'));
  page = await manager.submit(await manager.get(`orders.do?op=detail&id=${orderId}`), 'approve');
  assertPage(page);
  assert.match(page.body, /APPROVED/);

  const allocation = await invoke('daily-allocation', 'daily-allocation', [
    '--through', prior.today, '--limit', '1000', '--run-key', `${key}-allocation`
  ]);
  assert.match(allocation.stdout, /status=COMPLETED/);
  page = await warehouse.get(`orders.do?op=detail&id=${orderId}`);
  assertPage(page);
  assert.match(page.body, /ALLOCATED/);
  const instruction = links(page.body, page.url).find(link =>
    link.url.pathname.endsWith('/shipments.do') && link.url.searchParams.get('op') === 'new');
  assert.ok(instruction, 'CLI allocation did not enable the web shipping workflow');
  page = await warehouse.submit(await warehouse.get(instruction.url), 'instruct', {
    plannedDate: prior.periodEnd, quantity: ['3'], carrier: 'OWN', note: 'After standalone allocation'
  });
  assertPage(page);
  const shipmentId = page.url.searchParams.get('id');
  page = await warehouse.submit(page, 'confirm', {
    shippedDate: prior.periodEnd, trackingNumber: `${key}-SHIP`
  });
  assertPage(page);

  const monthly = await invoke('monthly-billing', 'monthly-billing', [
    '--period-end', prior.periodEnd, '--limit', '1000', '--run-key', `${key}-billing`, '--finalize'
  ]);
  assert.match(monthly.stdout, /status=COMPLETED/);
  page = await admin.get(`invoices.do?customerId=${customerId}`);
  assertPage(page);
  const invoices = links(page.body, page.url).filter(link =>
    link.url.pathname.endsWith('/invoices.do') && link.url.searchParams.get('op') === 'detail');
  assert.equal(invoices.length, 1, 'Monthly CLI must persist exactly one invoice for the new customer');
  const invoice = await admin.get(invoices[0].url);
  assertPage(invoice);
  assert.match(invoice.body, /FINALIZED/);
  const text = decodeHtml(invoice.body.replace(/<[^>]+>/g, ' ')).replace(/\s+/g, ' ');
  assert.match(text, /請求合計\s+330\.00\s+円/);
  console.log('PASS Web approval -> standalone daily allocation -> web shipment -> standalone monthly finalization');
  return { customerId, orderId, shipmentId, invoiceId: invoice.url.searchParams.get('id') };
}
