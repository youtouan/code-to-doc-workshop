import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { HttpClient, assertPage, findForm, links, suggestions } from './http-client.mjs';

function id(page) {
  const value = page.url.searchParams.get('id');
  assert.match(value || '', /^[1-9][0-9]*$/);
  return value;
}

export async function runApBoundaries(baseUrl) {
  const key = `AB${Date.now().toString(36)}${randomBytes(3).toString('hex')}`.toUpperCase();
  const admin = new HttpClient(baseUrl);
  const warehouse = new HttpClient(baseUrl);
  const manager = new HttpClient(baseUrl);
  const billing = new HttpClient(baseUrl);
  await admin.login('admin');
  await warehouse.login('warehouse');
  await manager.login('manager');
  await billing.login('billing');
  let page = await admin.get('products.do?op=new');
  page = await admin.submit(page, 'save', {
    code: `${key}P`, name: `境界値確認商品 ${key}`, unit: '個', active: 'true',
    taxCategory: 'STANDARD', listPrice: '500.00', standardCost: '320.00',
    packSize: '1', reorderPoint: '0', reorderQuantity: '0', notes: 'Isolated AP boundary fixture'
  });
  assertPage(page);
  const productId = id(page);
  page = await admin.submit(await admin.get('suppliers.do?op=new'), 'save', {
    code: `${key}S`, name: `境界値確認仕入先 ${key}`, active: 'true', onHold: 'false',
    closingDay: '31', paymentTermDays: '30', defaultLeadTimeDays: '0',
    minimumOrderAmount: '0.00', taxRounding: 'DOWN', address: '架空県境界市1-1'
  });
  assertPage(page);
  const supplierId = id(page);
  const termsPage = await admin.get(`supplierProducts.do?op=new&supplierId=${supplierId}`);
  const today = findForm(termsPage, 'save').fields.get('validFrom');
  assert.match(today || '', /^\d{4}-\d{2}-\d{2}$/);
  assertPage(await admin.submit(termsPage, 'save', {
    productId, supplierProductCode: `${key}-source`, validFrom: today,
    minimumQuantity: '1', orderPackSize: '1', leadTimeDays: '0', unitCost: '320.00',
    preferred: 'true', active: 'true'
  }));
  page = await warehouse.submit(await warehouse.get('purchases.do?op=new'), 'save', {
    supplierId, warehouseId: '21', orderDate: today, expectedDate: today,
    productId: [productId], quantity: ['1500'], lineExpectedDate: [today], lineNote: ['AP boundary receipts'],
    note: key
  });
  assertPage(page);
  const purchaseId = id(page);
  assertPage(await warehouse.submit(page, 'submit'));
  assertPage(await manager.submit(await manager.get(`purchases.do?op=detail&id=${purchaseId}`), 'approve'));
  const receiptIds = [];
  for (let part = 0; part < 3; part++) {
    page = await warehouse.get(`purchases.do?op=detail&id=${purchaseId}`);
    const receiving = links(page.body, page.url).find(link =>
      link.url.pathname.endsWith('/purchaseReceipts.do') && link.url.searchParams.get('op') === 'new');
    assert.ok(receiving);
    page = await warehouse.submit(await warehouse.get(receiving.url), 'receive', {
      requestKey: `${key}-receipt-${part}`, receiptDate: today, supplierDeliveryNumber: `${key}-delivery-${part}`,
      acceptedQuantity: ['500'], rejectedQuantity: ['0'], rejectionReason: [''], lineNote: ['Accepted goods']
    });
    assertPage(page);
    receiptIds.push(id(page));
  }

  // Empty stock via the approved adjustment workflow before deactivating the historical master.
  page = await warehouse.submit(await warehouse.get('adjustments.do?op=new'), 'propose', {
    warehouseId: '21', productId, quantityChange: '-1500', reason: 'Synthetic historical-stock disposal'
  });
  assertPage(page);
  assertPage(await manager.submit(await manager.get(`adjustments.do?op=detail&id=${id(page)}`), 'approve', {
    reason: 'Independent approval of synthetic disposal'
  }));
  assertPage(await admin.submit(await admin.get(`products.do?op=edit&id=${productId}`), 'save', { active: 'false' }));
  assertPage(await admin.submit(await admin.get(`suppliers.do?op=edit&id=${supplierId}`), 'save', { active: 'false' }));
  const supplierPage = await billing.get(`suppliers.do?op=detail&id=${supplierId}`);
  assertPage(supplierPage);
  const historicalLink = links(supplierPage.body, supplierPage.url).find(link =>
    link.url.pathname.endsWith('/apInvoices.do') && link.url.searchParams.get('op') === 'new');
  assert.ok(historicalLink, 'Inactive supplier detail must expose historical invoice entry');
  const entry = await billing.get(historicalLink.url);
  assertPage(entry);
  const invoiceForm = findForm(entry, 'save');
  assert.ok(invoiceForm.controls.some(control => control.name === 'productId' && control.type === 'text'),
    'Historical product selection must not be limited to active-product options');
  const lines = count => ({
    productId: Array(count).fill(productId),
    description: Array.from({ length: count }, (_, n) => `検収照合行 ${n + 1}`),
    quantity: Array(count).fill('6'), unitPrice: Array(count).fill('320.00'), taxRate: Array(count).fill('0.1')
  });
  const header = { supplierId, supplierInvoiceNumber: `${key}-invoice`, invoiceDate: today };
  const excessive = await billing.submit(entry, 'save', { ...header, ...lines(201) }, { follow: false });
  assert.equal(excessive.status, 400, '201 AP lines must be rejected before binding or persistence');
  page = await billing.submit(entry, 'save', { ...header, ...lines(200) });
  assertPage(page);
  const invoiceId = id(page);
  const invoiceEdit = await billing.get(`apInvoices.do?op=edit&id=${invoiceId}`);
  assertPage(invoiceEdit);
  assert.equal(findForm(invoiceEdit, 'save').fields.getAll('quantity').filter(Boolean).length, 200);
  console.log('PASS HTTP 200 actual AP lines, 201 rejection and inactive historical supplier/product selection');

  const matching = await billing.get(`apMatches.do?op=edit&id=${invoiceId}`);
  assertPage(matching);
  const form = findForm(matching, 'save');
  const invoiceLines = form.controls.find(control => control.name === 'invoiceLineId').options
    .map(option => option.value).filter(Boolean);
  assert.equal(invoiceLines.length, 200);
  const receiptLines = receiptIds.map(receipt => {
    const candidate = suggestions(matching.body).find(option => option.text.includes(`PR-${receipt.padStart(8, '0')}`));
    assert.ok(candidate, 'Accepted historical receipt must remain matchable');
    return candidate.value;
  });
  const matches = { invoiceLineId: [], receiptLineId: [], quantity: [] };
  for (let i = 0; i < 200; i++) {
    const parts = i < 100 ? 3 : 2;
    for (let part = 0; part < parts; part++) {
      matches.invoiceLineId.push(invoiceLines[i]);
      matches.receiptLineId.push(receiptLines[(i + part) % 3]);
      matches.quantity.push(String(6 / parts));
    }
  }
  assert.equal(matches.quantity.length, 500);
  const excessiveMatches = Object.fromEntries(Object.entries(matches).map(([name, values]) => [name, [...values, values[0]]]));
  const rejected = await billing.submit(matching, 'save', excessiveMatches, { follow: false });
  assert.equal(rejected.status, 400, '501 match rows must be rejected');
  page = await billing.submit(matching, 'save', matches);
  assertPage(page);
  const persistedMatches = await billing.get(`apMatches.do?op=edit&id=${invoiceId}`);
  assertPage(persistedMatches);
  assert.equal(findForm(persistedMatches, 'save').fields.getAll('quantity').filter(Boolean).length, 500);
  page = await billing.submit(await billing.get(`apInvoices.do?op=detail&id=${invoiceId}`), 'post');
  assertPage(page);
  assert.match(page.body, /POSTED/);
  console.log('PASS HTTP 500 persisted positive receipt matches, 501 rejection and exact fully matched AP posting');
  await mkdir('.runtime/smoke', { recursive: true });
  await writeFile('.runtime/smoke/ap-boundaries.json', JSON.stringify({
    key, supplierId, productId, purchaseId, receiptIds, invoiceId, invoiceLines: 200, matches: 500, result: 'PASS'
  }, null, 2), 'utf8');
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  runApBoundaries(process.argv[2] || 'http://127.0.0.1:18080/wholesale').catch(error => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
