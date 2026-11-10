import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { HttpClient, assertPage, decodeHtml, findForm, forms, links, withFields } from './http-client.mjs';

function identifier(page) {
  const id = page.url.searchParams.get('id');
  assert.match(id || '', /^[1-9][0-9]*$/, `Successful mutation did not redirect to a persisted document: ${page.url.pathname}`);
  return id;
}

function csrf(page) {
  const values = forms(page.body).map(form => form.fields.get('csrfToken')).filter(Boolean);
  assert.ok(values.length, 'No session CSRF token in rendered forms');
  return values[0];
}

function dateText(date) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Tokyo', year: 'numeric', month: '2-digit', day: '2-digit'
  }).formatToParts(date);
  const value = kind => parts.find(part => part.type === kind).value;
  return `${value('year')}-${value('month')}-${value('day')}`;
}

export async function runBusinessFlow(baseUrl) {
  const key = `H${Date.now().toString(36)}${randomBytes(3).toString('hex')}`.toUpperCase();
  const today = dateText(new Date());
  const admin = new HttpClient(baseUrl);
  const sales = new HttpClient(baseUrl);
  const manager = new HttpClient(baseUrl);
  const warehouse = new HttpClient(baseUrl);
  const billing = new HttpClient(baseUrl);
  await admin.login('admin');
  await sales.login('sales');
  await manager.login('manager');
  await warehouse.login('warehouse');
  await billing.login('billing');

  const customerName = 'HTTP <b>架空商店</b> & 用品';
  let page = await admin.get('customers.do?op=new');
  page = await admin.submit(page, 'save', {
    code: `${key}C`, name: customerName, active: 'true', onHold: 'false',
    creditLimit: '1000000.00', closingDay: '31', paymentTermDays: '30', taxRounding: 'DOWN',
    postalCode: '000-0000', address: '架空県HTTP市1-1', telephone: '000-000-0000', notes: 'HTTP workflow'
  });
  assertPage(page);
  const customerId = identifier(page);
  assert.ok(page.body.includes('&lt;b&gt;'), 'Persisted customer text must be HTML escaped');
  assert.ok(!page.body.includes('<b>架空商店</b>'), 'Persisted customer text became active markup');

  page = await admin.get('products.do?op=new');
  page = await admin.submit(page, 'save', {
    code: `${key}P`, name: `HTTP検証商品 ${key}`, unit: '個', active: 'true',
    taxCategory: 'STANDARD', listPrice: '100.00', standardCost: '60.00',
    packSize: '1', reorderPoint: '5', reorderQuantity: '20', notes: 'HTTP workflow'
  });
  assertPage(page);
  const productId = identifier(page);
  console.log('PASS HTTP master maintenance, persistent IDs and escaped output');

  const receiptFormPage = await warehouse.get('receipts.do?op=new');
  const receiptChanges = {
    requestKey: `${key}-receipt`, warehouseId: '21', productId, quantity: '25',
    unitCost: '60.00', receiptDate: today, reference: `${key}-delivery`, note: 'HTTP workflow receipt'
  };
  page = await warehouse.submit(receiptFormPage, 'receive', receiptChanges);
  assertPage(page);
  const receiptId = identifier(page);
  const duplicateReceipt = await warehouse.submit(receiptFormPage, 'receive', receiptChanges);
  assertPage(duplicateReceipt);
  assert.equal(identifier(duplicateReceipt), receiptId, 'Repeated receipt must retain its original persistent ID');
  console.log('PASS HTTP receiving and identical request-key rerun');

  page = await sales.get('orders.do?op=new');
  const invalid = await sales.submit(page, 'save', {
    customerId, warehouseId: '21', orderDate: today, requestedDate: today,
    productId: [productId], quantity: ['-1'], priceOverride: [''], priceReason: [''],
    externalReference: `${key}-invalid`, deliveryAddress: '架空県HTTP市1-1', notes: 'Invalid quantity case'
  });
  assert.equal(invalid.status, 422, 'Invalid quantity must produce a real form error, not a success redirect');
  const draftPage = await sales.get('orders.do?op=new');
  page = await sales.submit(draftPage, 'save', {
    customerId, warehouseId: '21', orderDate: today, requestedDate: today,
    productId: [productId], quantity: ['10'], priceOverride: [''], priceReason: [''],
    externalReference: `${key}-order`, deliveryAddress: '架空県HTTP市1-1', notes: 'HTTP workflow order'
  });
  assertPage(page);
  const orderId = identifier(page);
  const initialCancel = findForm(page, 'cancel');
  const forgedGet = new URL(initialCancel.action, page.url);
  forgedGet.search = withFields(initialCancel, { reason: 'A GET must not cancel this order' }).toString();
  const refusedGet = await sales.get(forgedGet);
  assert.equal(refusedGet.status, 405, 'A fully populated mutation form must still reject GET');
  page = await sales.get(`orders.do?op=detail&id=${orderId}`);
  assertPage(page);
  findForm(page, 'submit');
  page = await sales.submit(page, 'submit');
  assertPage(page);

  const approvalPage = await manager.get(`orders.do?op=detail&id=${orderId}`);
  const approvalForm = findForm(approvalPage, 'approve');
  const salesPage = await sales.get(`orders.do?op=detail&id=${orderId}`);
  const forgedApproval = await sales.request(new URL(approvalForm.action, approvalPage.url), {
    method: 'POST', fields: withFields(approvalForm, { csrfToken: csrf(salesPage) }), follow: false
  });
  assert.equal(forgedApproval.status, 403, 'A valid authenticated sales request must not approve an order');
  page = await manager.submit(await manager.get(`orders.do?op=detail&id=${orderId}`), 'approve');
  assertPage(page);
  console.log('PASS HTTP draft validation, safe methods, submission and server-side approval permissions');

  page = await warehouse.get(`orders.do?op=detail&id=${orderId}`);
  page = await warehouse.submit(page, 'allocate');
  assertPage(page);
  const allocatedPage = await sales.get(`orders.do?op=detail&id=${orderId}`);
  const staleForm = findForm(allocatedPage, 'cancel');
  page = await sales.submit(allocatedPage, 'cancel', { reason: 'HTTP verification cancellation' });
  assertPage(page);
  const repeatCancellation = await sales.request(new URL(staleForm.action, allocatedPage.url), {
    method: 'POST', fields: withFields(staleForm, { reason: 'Repeated stale request' }), follow: false
  });
  assert.ok([409, 422].includes(repeatCancellation.status), 'Stale mutation must not report success');
  const cancelled = await sales.get(`orders.do?op=detail&id=${orderId}`);
  assertPage(cancelled);
  assert.ok(!forms(cancelled.body).some(form => form.fields.get('op') === 'allocate'
    || form.buttons.some(button => button.name === 'op' && button.value === 'allocate')),
  'Cancelled order still exposes an allocation operation');
  console.log('PASS HTTP allocation, cancellation and stale-version rejection');

  const [year, month] = today.split('-').map(Number);
  const closingDate = new Date(Date.UTC(year, month - 1, 0));
  const earlier = new Date(closingDate);
  earlier.setUTCDate(earlier.getUTCDate() - 2);
  const periodEnd = closingDate.toISOString().slice(0, 10);
  page = await sales.submit(await sales.get('orders.do?op=new'), 'save', {
    customerId, warehouseId: '21', orderDate: earlier.toISOString().slice(0, 10), requestedDate: periodEnd,
    productId: [productId], quantity: ['8'], priceOverride: [''], priceReason: [''],
    externalReference: `${key}-shipping`, deliveryAddress: '架空県HTTP市1-1', notes: 'Partial shipping and billing'
  });
  assertPage(page);
  const shippingOrderId = identifier(page);
  assertPage(await sales.submit(page, 'submit'));
  assertPage(await manager.submit(await manager.get(`orders.do?op=detail&id=${shippingOrderId}`), 'approve'));
  page = await warehouse.submit(await warehouse.get(`orders.do?op=detail&id=${shippingOrderId}`), 'allocate');
  assertPage(page);
  const instructionLink = links(page.body, page.url).find(link =>
    link.url.pathname.endsWith('/shipments.do') && link.url.searchParams.get('op') === 'new');
  assert.ok(instructionLink, 'Allocated order must expose the shipment instruction workflow');
  page = await warehouse.submit(await warehouse.get(instructionLink.url), 'instruct', {
    plannedDate: periodEnd, carrier: 'OWN', quantity: ['4'], note: 'Partial shipment through HTTP'
  });
  assertPage(page);
  const shipmentId = identifier(page);
  const confirmForm = findForm(page, 'confirm');
  const shipmentBefore = page;
  page = await warehouse.submit(page, 'confirm', { shippedDate: periodEnd, trackingNumber: `${key}-TRACK` });
  assertPage(page);
  const duplicateShipment = await warehouse.request(new URL(confirmForm.action, shipmentBefore.url), {
    method: 'POST',
    fields: withFields(confirmForm, { shippedDate: periodEnd, trackingNumber: `${key}-TRACK` }),
    follow: false
  });
  assert.ok([409, 422].includes(duplicateShipment.status), 'Confirmed shipment must not consume inventory twice');
  const partialOrder = await sales.get(`orders.do?op=detail&id=${shippingOrderId}`);
  assertPage(partialOrder);
  assert.match(partialOrder.body, /PART_SHIPPED/);
  console.log('PASS HTTP partial shipment confirmation and duplicate prevention');

  page = await billing.submit(await billing.get(`invoices.do?op=new&periodEnd=${periodEnd}`), 'prepare', {
    customerId, periodEnd
  });
  assertPage(page);
  const invoiceId = identifier(page);
  let invoiceText = decodeHtml(page.body.replace(/<[^>]+>/g, ' ')).replace(/\s+/g, ' ');
  assert.match(invoiceText, /税抜 \/ 消費税\s+400\.00\s+\/\s+40\.00/);
  assert.match(invoiceText, /請求合計\s+440\.00\s+円/);
  page = await billing.submit(page, 'finalize');
  assertPage(page);
  assert.match(page.body, /FINALIZED/);
  const repeatedInvoice = await billing.submit(await billing.get(`invoices.do?op=new&periodEnd=${periodEnd}`), 'prepare', {
    customerId, periodEnd
  });
  assertPage(repeatedInvoice);
  assert.equal(identifier(repeatedInvoice), invoiceId, 'Same closing period must not create a second invoice');
  page = await sales.submit(await sales.get(`orders.do?op=detail&id=${shippingOrderId}`), 'cancel', {
    reason: 'Cancel only unshipped remainder after partial billing'
  });
  assertPage(page);
  assert.match(page.body, /CLOSED_PARTIAL/);
  const invoiceAfter = await billing.get(`invoices.do?op=detail&id=${invoiceId}`);
  assertPage(invoiceAfter);
  invoiceText = decodeHtml(invoiceAfter.body.replace(/<[^>]+>/g, ' ')).replace(/\s+/g, ' ');
  assert.match(invoiceText, /請求合計\s+440\.00\s+円/);
  console.log('PASS HTTP closing-period invoice totals, finalization, idempotency and partial-order cancellation');

  page = await billing.submit(await billing.get(`payments.do?op=new&customerId=${customerId}`), 'receive', {
    requestKey: `${key}-payment`, customerId, receivedDate: today, amount: '100.00',
    method: 'BANK_TRANSFER', reference: `${key}-bank`, notes: 'Manual payment received'
  });
  assertPage(page);
  const paymentId = identifier(page);
  page = await billing.submit(page, 'allocate', { invoiceId, amount: '100.00', reason: 'HTTP invoice allocation' });
  assertPage(page);
  const shipmentPage = await sales.get(`shipments.do?op=detail&id=${shipmentId}`);
  assertPage(shipmentPage);
  const returnLink = links(shipmentPage.body, shipmentPage.url).find(link =>
    link.url.pathname.endsWith('/returns.do') && link.url.searchParams.get('op') === 'new');
  assert.ok(returnLink, 'A confirmed shipment must provide the return workflow');
  page = await sales.submit(await sales.get(returnLink.url), 'request', {
    quantity: ['1'], restock: ['true'], returnReason: 'CUSTOMER_CHANGE', notes: 'HTTP return after partial payment'
  });
  assertPage(page);
  const returnId = identifier(page);
  page = await manager.submit(await manager.get(`returns.do?op=detail&id=${returnId}`), 'approve');
  assertPage(page);
  page = await warehouse.submit(await warehouse.get(`returns.do?op=detail&id=${returnId}`), 'receive', {
    receivedDate: today
  });
  assertPage(page);
  assert.match(page.body, /RECEIVED/);
  const creditedInvoice = await billing.get(`invoices.do?op=detail&id=${invoiceId}`);
  assertPage(creditedInvoice);
  const creditedText = decodeHtml(creditedInvoice.body.replace(/<[^>]+>/g, ' ')).replace(/\s+/g, ' ');
  assert.match(creditedText, /消込済 \/ クレジット\s+100\.00\s+\/\s+110\.00/);
  assert.match(creditedText, /請求残高\s+230\.00\s+円/);
  console.log('PASS HTTP payment allocation, approved return receipt and exact credit-adjusted invoice balance');

  const login = `${key.toLowerCase()}u`;
  const userPassword = `Demo-${login}-2026!`;
  page = await admin.submit(await admin.get('users.do?op=new'), 'create', {
    login, displayName: `HTTP権限確認 ${key}`, selectedRole: ['SALES'],
    newPassword: userPassword, confirmation: userPassword
  });
  assertPage(page);
  const userId = identifier(page);
  assert.ok(!page.body.includes(userPassword), 'User administration must not echo a password');
  const changedUser = new HttpClient(baseUrl);
  await changedUser.login(login);
  const deniedAdministration = await changedUser.request('users.do', { follow: false });
  assert.equal(deniedAdministration.status, 403);
  const capturedDraft = await changedUser.get('orders.do?op=new');
  assertPage(capturedDraft);
  assertPage(await admin.submit(await admin.get(`users.do?op=edit&id=${userId}`), 'update', {
    displayName: `HTTP権限確認 ${key}`, selectedRole: ['WAREHOUSE'], active: 'true'
  }));
  const revokedWrite = await changedUser.submit(capturedDraft, 'save', {
    customerId, warehouseId: '21', orderDate: today, requestedDate: today,
    productId: [productId], quantity: ['1'], priceOverride: [''], priceReason: [''],
    externalReference: `${key}-revoked`, deliveryAddress: '架空県HTTP市1-1', notes: 'Must not be created'
  });
  assert.equal(revokedWrite.status, 403, 'A previously rendered form must not retain revoked server permissions');
  assertPage(await changedUser.get('receipts.do?op=new'));
  assertPage(await admin.submit(await admin.get(`users.do?op=edit&id=${userId}`), 'update', {
    displayName: `HTTP権限確認 ${key}`, selectedRole: ['WAREHOUSE'], active: 'false'
  }));
  const disabled = await changedUser.request('dashboard.do', { follow: false });
  assert.ok([302, 303, 401, 403].includes(disabled.status), 'Disabled account retained an authenticated dashboard');
  console.log('PASS HTTP account administration, immediate permission refresh and active-session disable');

  await mkdir('.runtime/smoke', { recursive: true });
  await writeFile('.runtime/smoke/http-fixture.json', JSON.stringify({
    key, customerId, productId, receiptId, orderId, shippingOrderId, shipmentId, invoiceId,
    paymentId, returnId, userId, periodEnd, today, result: 'PASS'
  }, null, 2), 'utf8');
  return { key, customerId, productId, receiptId, orderId, shippingOrderId, shipmentId, invoiceId, paymentId, returnId };
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  runBusinessFlow(process.argv[2] || 'http://127.0.0.1:18080/wholesale').catch(error => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
