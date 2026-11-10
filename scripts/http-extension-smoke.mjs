import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { HttpClient, assertPage, decodeHtml, forms, withFields } from './http-client.mjs';

function id(page, label = 'document') {
  const value = page.url.searchParams.get('id');
  assert.match(value || '', /^[1-9][0-9]*$/, `${label} mutation did not redirect to a persisted detail page`);
  return value;
}

function bodyText(page) {
  return decodeHtml(page.body.replace(/<[^>]+>/g, ' ')).replace(/\s+/g, ' ');
}

function latestDeliveryId(page) {
  const match = bodyText(page).match(/履歴の最新記録ID\s+([1-9][0-9]*)/);
  assert.ok(match, 'Delivery detail must expose the persisted latest-event cursor');
  return match[1];
}

function dateText(date) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Tokyo', year: 'numeric', month: '2-digit', day: '2-digit'
  }).formatToParts(date);
  const value = type => parts.find(part => part.type === type).value;
  return `${value('year')}-${value('month')}-${value('day')}`;
}

function dayOffset(days) {
  return dateText(new Date(Date.now() + days * 86400000));
}

function hasOperation(form, operation) {
  return form.fields.get('op') === operation
    || form.buttons.some(button => button.name === 'op' && button.value === operation);
}

function findOperation(page, operations) {
  const choices = Array.isArray(operations) ? operations : [operations];
  const candidates = [];
  for (const form of forms(page.body)) {
    for (const operation of choices) {
      if (hasOperation(form, operation)) {
        const fields = new URLSearchParams(form.fields);
        fields.set('op', operation);
        candidates.push({ operation, form: { ...form, fields } });
      }
    }
  }
  assert.equal(candidates.length, 1,
    `Expected one ${choices.join('/')} form at ${page.url.pathname}${page.url.search}`);
  return candidates[0];
}

async function submitAny(client, page, operations, changes = {}, options = {}) {
  const { operation, form } = findOperation(page, operations);
  assert.equal(form.method, 'POST', `${operation} must use POST`);
  return client.request(new URL(form.action, page.url), {
    method: 'POST',
    fields: withFields(form, changes),
    follow: options.follow !== false
  });
}

async function postFrom(page, client, operations, changes = {}, follow = false) {
  const { form } = findOperation(page, operations);
  return client.request(new URL(form.action, page.url), {
    method: 'POST',
    fields: withFields(form, changes),
    follow
  });
}

function fieldValues(form, names) {
  for (const name of names) {
    const values = form.fields.getAll(name).filter(value => value.length > 0);
    if (values.length) return values;
    const controls = form.controls.filter(control => control.name === name && control.value);
    if (controls.length) return controls.map(control => control.value);
  }
  return [];
}

function fieldNameSet(form) {
  return new Set([...form.fields.keys(), ...form.controls.map(control => control.name).filter(Boolean)]);
}

function assertStatus(page, statuses, message) {
  assert.ok(statuses.includes(page.status), `${message}: returned ${page.status}`);
}

async function assertMutationRejectsGet(page, client, operations, changes, message) {
  const { form } = findOperation(page, operations);
  const url = new URL(form.action, page.url);
  url.search = withFields(form, changes).toString();
  const denied = await client.get(url);
  assertStatus(denied, [403, 405], message);
}

async function assertMissingCsrfDenied(page, client, operations, changes, message) {
  const denied = await postFrom(page, client, operations, { ...changes, csrfToken: null }, false);
  assert.equal(denied.status, 403, message);
}

async function ensureExtensionSurface(baseUrl, ctx, selected) {
  const admin = new HttpClient(baseUrl);
  await admin.login('admin');
  const probes = [
    ['quotes', 'quotation list', 'quotations.do'],
    ['quotes', 'quotation entry', 'quotations.do?op=new'],
    ['amendments', 'order amendment list', 'orderAmendments.do'],
    ['dispatch', 'dispatch manifest list', 'dispatchManifests.do'],
    ['dispatch', 'delivery attempt queue', 'deliveryAttempts.do'],
    ['reports', 'operations reports', 'operationsReports.do']
  ];
  const blockers = [];
  for (const [group, name, path] of probes) {
    if (!selected.includes(group)) continue;
    try {
      const page = await admin.get(path);
      if (page.status !== 200) {
        blockers.push(`${path} returned ${page.status} (${name}, request=${page.headers.get('x-request-id') || 'none'})`);
      } else if (!page.body.includes('</html>')) {
        blockers.push(`${path} did not render a complete HTML page (${name})`);
      }
    } catch (error) {
      blockers.push(`${path} failed (${name}): ${error.message}`);
    }
  }
  if (blockers.length) {
    ctx.blockers = blockers;
    throw new Error(`Extension UI contract blocker: ${blockers.join('; ')}`);
  }
}

async function createMasterData(admin, warehouse, key) {
  const today = dayOffset(0);
  let page = await admin.get('customers.do?op=new');
  page = await admin.submit(page, 'save', {
    code: `${key}C`, name: `HTTP拡張得意先 ${key}`, active: 'true', onHold: 'false',
    creditLimit: '1000000.00', closingDay: '31', paymentTermDays: '30', taxRounding: 'DOWN',
    postalCode: '000-0000', address: '架空県拡張市1-1', telephone: '000-000-0000', notes: 'HTTP extension smoke'
  });
  assertPage(page);
  const customerId = id(page, 'customer');

  page = await admin.get('products.do?op=new');
  page = await admin.submit(page, 'save', {
    code: `${key}P`, name: `HTTP拡張商品 ${key}`, unit: '個', active: 'true',
    taxCategory: 'STANDARD', listPrice: '120.00', standardCost: '70.00',
    packSize: '1', reorderPoint: '5', reorderQuantity: '40', notes: 'HTTP extension smoke'
  });
  assertPage(page);
  const productId = id(page, 'product');

  page = await warehouse.submit(await warehouse.get('receipts.do?op=new'), 'receive', {
    requestKey: `${key}-stock`, warehouseId: '21', productId, quantity: '80',
    unitCost: '70.00', receiptDate: today, reference: `${key}-stock`, note: 'HTTP extension fixture stock'
  });
  assertPage(page);
  return { customerId, productId, today, future: dayOffset(3), tomorrow: dayOffset(1) };
}

async function createApprovedAllocatedOrder({ sales, manager, warehouse, customerId, productId, quantity, key, suffix }) {
  const today = dayOffset(0);
  const requested = dayOffset(3);
  let page = await sales.submit(await sales.get('orders.do?op=new'), 'save', {
    customerId, warehouseId: '21', orderDate: today, requestedDate: requested,
    productId: [productId], quantity: [String(quantity)], priceOverride: [''], priceReason: [''],
    externalReference: `${key}-${suffix}`, deliveryAddress: '架空県拡張市1-1', notes: `HTTP extension ${suffix}`
  });
  assertPage(page);
  const orderId = id(page, 'order');
  assertPage(await sales.submit(page, 'submit'));
  assertPage(await manager.submit(await manager.get(`orders.do?op=detail&id=${orderId}`), 'approve'));
  page = await warehouse.submit(await warehouse.get(`orders.do?op=detail&id=${orderId}`), 'allocate');
  assertPage(page);
  return { orderId };
}

async function instructShipment(warehouse, orderId, quantity, key) {
  const page = await warehouse.get(`shipments.do?op=new&orderId=${orderId}`);
  assertPage(page);
  const { form } = findOperation(page, 'instruct');
  const lineIds = fieldValues(form, ['lineId', 'orderLineId']);
  assert.ok(lineIds.length, 'Shipment instruction form must expose order line identifiers');
  const quantities = lineIds.map((_, index) => index === 0 ? String(quantity) : '0');
  const detail = await submitAny(warehouse, page, 'instruct', {
    plannedDate: dayOffset(0), carrier: 'OWN', lineId: lineIds,
    quantity: quantities, note: `${key} shipment instruction`
  });
  assertPage(detail);
  const shipmentId = id(detail, 'shipment');
  const refreshed = await warehouse.get(`shipments.do?op=detail&id=${shipmentId}`);
  assertPage(refreshed);
  const detailForm = findOperation(refreshed, 'confirm').form;
  const version = detailForm.fields.get('version');
  assert.match(version || '', /^[0-9]+$/, 'Shipment form must carry its actual version');
  return { shipmentId, version, page: refreshed };
}

async function confirmSingleShipment(warehouse, shipmentId, key) {
  const page = await warehouse.get(`shipments.do?op=detail&id=${shipmentId}`);
  const confirmed = await warehouse.submit(page, 'confirm', {
    shippedDate: dayOffset(0), trackingNumber: `${key}-SINGLE-${shipmentId}`
  });
  assertPage(confirmed);
  return confirmed;
}

async function runQuotationAcceptance(baseUrl, ids, ctx) {
  const sales = new HttpClient(baseUrl);
  const manager = new HttpClient(baseUrl);
  await sales.login('sales');
  await manager.login('manager');

  const quotePage = await sales.get('quotations.do?op=new');
  assertPage(quotePage);
  const quoteChanges = {
    customerId: ids.customerId, warehouseId: '21', quoteDate: ids.today, validUntil: ids.future,
    requestedDate: ids.future, deliveryAddress: '架空県拡張市1-1', externalReference: `${ctx.key}-quote`,
    notes: 'HTTP accepted quotation', productId: [ids.productId], quantity: ['7'],
    negotiatedUnitPrice: ['88.00'], negotiationReason: ['HTTP negotiated acceptance price']
  };
  await assertMissingCsrfDenied(quotePage, sales, 'save', quoteChanges,
    'Quotation save must reject missing CSRF without creating a draft');
  await assertMutationRejectsGet(quotePage, sales, 'save', quoteChanges, 'Quotation save must reject GET');
  let page = await submitAny(sales, quotePage, 'save', quoteChanges);
  assertPage(page);
  const quotationId = id(page, 'quotation');
  assert.match(bodyText(page), /88\.00/);
  assert.match(bodyText(page), /HTTP negotiated acceptance price/);

  page = await submitAny(sales, page, 'submit');
  assertPage(page);
  const managerApproval = await manager.get(`quotations.do?op=detail&id=${quotationId}`);
  assertPage(managerApproval);
  const salesView = await sales.get(`quotations.do?op=detail&id=${quotationId}`);
  const forbiddenApproval = await postFrom(managerApproval, sales, 'approve', {
    csrfToken: forms(salesView.body).map(form => form.fields.get('csrfToken')).find(Boolean)
  }, false);
  assert.equal(forbiddenApproval.status, 403, 'Sales role must not approve its own quotation');

  const staleApproval = managerApproval;
  page = await submitAny(manager, managerApproval, 'approve');
  assertPage(page);
  const replayApproval = await postFrom(staleApproval, manager, 'approve', {}, false);
  assertStatus(replayApproval, [409, 422], 'Stale quotation approval must not succeed twice');

  page = await submitAny(sales, await sales.get(`quotations.do?op=detail&id=${quotationId}`),
    'accept', {
      acceptedOn: ids.today, customerReference: `${ctx.key}-accepted`
    });
  assertPage(page);
  const convertSource = await sales.get(`quotations.do?op=detail&id=${quotationId}`);
  page = await submitAny(sales, convertSource, 'convert');
  assertPage(page);
  const orderId = id(page, 'converted order');
  assert.ok(page.url.pathname.endsWith('/orders.do'), 'Quotation conversion must redirect to the converted sales order');
  const convertedText = bodyText(page);
  assert.match(convertedText, /88\.00/);
  assert.match(convertedText, /HTTP negotiated acceptance price/);
  const replayConvert = await postFrom(convertSource, sales, 'convert', {}, true);
  assertPage(replayConvert);
  assert.equal(id(replayConvert, 'replayed converted order'), orderId,
    'Repeated quotation conversion must return the original sales order');
  ctx.quotation = { quotationId, orderId };
}

async function runOrderAmendmentAcceptance(baseUrl, ids, ctx) {
  const sales = new HttpClient(baseUrl);
  const manager = new HttpClient(baseUrl);
  const warehouse = new HttpClient(baseUrl);
  await sales.login('sales');
  await manager.login('manager');
  await warehouse.login('warehouse');

  const order = await createApprovedAllocatedOrder({
    sales, manager, warehouse, customerId: ids.customerId, productId: ids.productId,
    quantity: 8, key: ctx.key, suffix: 'amend'
  });
  const shipment = await instructShipment(warehouse, order.orderId, 3, ctx.key);
  assertPage(await confirmSingleShipment(warehouse, shipment.shipmentId, ctx.key));

  const newPage = await sales.get(`orderAmendments.do?op=new&orderId=${order.orderId}`);
  assertPage(newPage);
  const { form: requestForm } = findOperation(newPage, 'request');
  const names = fieldNameSet(requestForm);
  assert.ok(names.has('orderLineId'),
    'Order amendment form must expose source order line identifiers');
  const lineIds = fieldValues(requestForm, ['orderLineId']);
  assert.ok(lineIds.length, 'Order amendment form did not include editable lines');
  const expectedOrderVersion = requestForm.fields.get('expectedOrderVersion');
  assert.match(expectedOrderVersion || '', /^[0-9]+$/, 'Amendment requires the actual source-order version');
  const amendmentChanges = {
    orderId: order.orderId, expectedOrderVersion,
    requestedDate: ids.future, reason: 'HTTP quantity reduction after partial shipment',
    orderLineId: lineIds,
    targetQuantity: lineIds.map((_, index) => index === 0 ? '5' : '')
  };
  let page = await submitAny(sales, newPage, 'request', amendmentChanges);
  assertPage(page);
  const amendmentId = id(page, 'order amendment');
  const managerApproval = await manager.get(`orderAmendments.do?op=detail&id=${amendmentId}`);
  assertPage(managerApproval);
  const salesView = await sales.get(`orderAmendments.do?op=detail&id=${amendmentId}`);
  const forbidden = await postFrom(managerApproval, sales, 'approve', {
    csrfToken: forms(salesView.body).map(form => form.fields.get('csrfToken')).find(Boolean)
  }, false);
  assert.equal(forbidden.status, 403, 'Requester must not independently approve their own amendment');
  page = await submitAny(manager, managerApproval, 'approve', {
    reason: 'Independent manager approval of quantity reduction'
  });
  assertPage(page);
  assert.match(bodyText(page), /APPLIED/);
  const staleRequest = await postFrom(newPage, sales, 'request', amendmentChanges, false);
  assertStatus(staleRequest, [409, 422], 'Stale amendment request must not apply to a changed order');

  const orderPage = await sales.get(`orders.do?op=detail&id=${order.orderId}`);
  assertPage(orderPage);
  const orderText = bodyText(orderPage);
  assert.match(orderText, /PART_SHIPPED|ALLOCATED|APPROVED/);
  assert.match(orderText, /\b5\b[\s\S]*2 \/ 3[\s\S]*2 \/ 0/,
    'Amended order must keep shipped quantity at 3 and release surplus reservation to 2');
  const shipmentPage = await warehouse.get(`shipments.do?op=detail&id=${shipment.shipmentId}`);
  assertPage(shipmentPage);
  assert.match(bodyText(shipmentPage), /CONFIRMED/);
  assert.match(bodyText(shipmentPage), /\b3\b/, 'Confirmed shipment history quantity must remain visible');
  ctx.amendment = { orderId: order.orderId, amendmentId, shipmentId: shipment.shipmentId };
}

async function runDispatchAndDeliveryAcceptance(baseUrl, ids, ctx) {
  const sales = new HttpClient(baseUrl);
  const warehouse = new HttpClient(baseUrl);
  const manager = new HttpClient(baseUrl);
  await sales.login('sales');
  await warehouse.login('warehouse');
  await manager.login('manager');

  const first = await createApprovedAllocatedOrder({
    sales, manager, warehouse, customerId: ids.customerId, productId: ids.productId,
    quantity: 4, key: ctx.key, suffix: 'dispatch-a'
  });
  const second = await createApprovedAllocatedOrder({
    sales, manager, warehouse, customerId: ids.customerId, productId: ids.productId,
    quantity: 6, key: ctx.key, suffix: 'dispatch-b'
  });
  const shipmentA = await instructShipment(warehouse, first.orderId, 4, ctx.key);
  const shipmentB = await instructShipment(warehouse, second.orderId, 6, ctx.key);

  const manifestEntry = await warehouse.get('dispatchManifests.do?op=new&warehouseId=21&carrier=OWN');
  assertPage(manifestEntry);
  const { form: manifestForm } = findOperation(manifestEntry, 'save');
  const manifestNames = fieldNameSet(manifestForm);
  assert.ok(manifestNames.has('shipmentChoice'),
    'Dispatch manifest form must expose grouped shipment identifiers');
  let page = await submitAny(warehouse, manifestEntry, 'save', {
    warehouseId: '21', carrier: 'OWN', plannedDispatchDate: ids.today,
    shipmentChoice: [`${shipmentA.shipmentId}:${shipmentA.version}`, `${shipmentB.shipmentId}:${shipmentB.version}`],
    stopNote: ['HTTP grouped stop A', 'HTTP grouped stop B'],
    note: 'HTTP grouped dispatch manifest'
  });
  assertPage(page);
  const manifestId = id(page, 'dispatch manifest');
  const releasePage = await warehouse.get(`dispatchManifests.do?op=detail&id=${manifestId}`);
  const salesView = await sales.get(`dispatchManifests.do?op=detail&id=${manifestId}`);
  const forbiddenRelease = await postFrom(releasePage, sales, 'release', {
    csrfToken: forms(salesView.body).map(form => form.fields.get('csrfToken')).find(Boolean)
  }, false);
  assert.equal(forbiddenRelease.status, 403, 'Sales role must not release a dispatch manifest');
  page = await submitAny(warehouse, releasePage, 'release');
  assertPage(page);
  assert.match(bodyText(page), /RELEASED/);
  const replayRelease = await postFrom(releasePage, warehouse, 'release', {}, false);
  assertStatus(replayRelease, [409, 422], 'Released dispatch manifest must reject stale release input');

  page = await submitAny(warehouse, page, 'confirm', {
    dispatchDate: ids.today,
    shipmentId: [shipmentA.shipmentId, shipmentB.shipmentId],
    trackingReference: [`${ctx.key}-TRK-A`, `${ctx.key}-TRK-B`]
  });
  assertPage(page);
  assert.match(bodyText(page), /DISPATCHED/);
  for (const shipment of [shipmentA, shipmentB]) {
    const shipped = await warehouse.get(`shipments.do?op=detail&id=${shipment.shipmentId}`);
    assertPage(shipped);
    assert.match(bodyText(shipped), /CONFIRMED/);
  }

  const firstAttemptPage = await warehouse.get(`deliveryAttempts.do?op=new&shipmentId=${shipmentA.shipmentId}`);
  assertPage(firstAttemptPage);
  page = await submitAny(warehouse, firstAttemptPage, ['record', 'save'], {
    shipmentId: shipmentA.shipmentId, expectedLatestEventId: null, requestKey: `${ctx.key}-delivery-failed`,
    attemptAt: `${ids.today}T00:00:00.000`, outcome: 'FAILED', reportingCompany: 'HTTP Carrier',
    evidenceReference: `${ctx.key}-MISS`, reason: '不在のため持ち戻り', nextAttemptDate: null
  });
  assertPage(page);
  assert.equal(id(page, 'delivery shipment'), shipmentA.shipmentId);
  const failedId = latestDeliveryId(page);
  const duplicateFailure = await submitAny(warehouse, firstAttemptPage, ['record', 'save'], {
    shipmentId: shipmentA.shipmentId, expectedLatestEventId: null, requestKey: `${ctx.key}-delivery-failed`,
    attemptAt: `${ids.today}T00:00:00.000`, outcome: 'FAILED', reportingCompany: 'HTTP Carrier',
    evidenceReference: `${ctx.key}-MISS`, reason: '不在のため持ち戻り', nextAttemptDate: null
  });
  assertPage(duplicateFailure);
  assert.equal(latestDeliveryId(duplicateFailure), failedId,
    'Delivery report request key must make identical manual reports idempotent');

  const reschedulePage = await warehouse.get(`deliveryAttempts.do?op=new&shipmentId=${shipmentA.shipmentId}`);
  page = await submitAny(warehouse, reschedulePage, ['record', 'save'], {
    shipmentId: shipmentA.shipmentId, expectedLatestEventId: failedId, requestKey: `${ctx.key}-delivery-rescheduled`,
    attemptAt: `${ids.today}T00:00:00.001`, outcome: 'RESCHEDULED', reportingCompany: 'HTTP Carrier',
    evidenceReference: `${ctx.key}-NEXT`, reason: '顧客依頼により再配送',
    nextAttemptDate: ids.tomorrow
  });
  assertPage(page);
  const rescheduledId = latestDeliveryId(page);
  const historyPage = await manager.get(`deliveryAttempts.do?op=detail&shipmentId=${shipmentA.shipmentId}`);
  assertPage(historyPage);
  const editors = forms(historyPage.body).filter(form => form.fields.get('op') === 'editCorrection'
    && form.fields.get('targetEventId') === rescheduledId);
  assert.equal(editors.length, 1, 'Bounded delivery history must expose the exact correction target');
  const correctionPage = await manager.request(new URL(editors[0].action, historyPage.url), {
    method: 'POST', fields: editors[0].fields
  });
  assertPage(correctionPage);
  page = await submitAny(manager, correctionPage, ['correct', 'correction'], {
    shipmentId: shipmentA.shipmentId, targetEventId: rescheduledId, expectedLatestEventId: rescheduledId,
    requestKey: `${ctx.key}-delivery-corrected`, attemptAt: `${ids.today}T00:00:00.001`, outcome: 'DELIVERED',
    reportingCompany: 'HTTP Carrier', evidenceReference: `${ctx.key}-DONE`,
    reason: '手入力の配送証跡を確認', correctionReason: '再配送予定を配達完了へ訂正', nextAttemptDate: null
  });
  assertPage(page);
  const correctedId = latestDeliveryId(page);
  const staleDelivery = await postFrom(reschedulePage, warehouse, ['record', 'save'], {
    shipmentId: shipmentA.shipmentId, expectedLatestEventId: failedId, requestKey: `${ctx.key}-delivery-stale`,
    attemptAt: `${ids.today}T00:00:00.002`, outcome: 'FAILED', reportingCompany: 'HTTP Carrier',
    evidenceReference: `${ctx.key}-STALE`, reason: '古い配送履歴カーソル', nextAttemptDate: null
  }, false);
  assertStatus(staleDelivery, [409, 422], 'Delivery report must reject a stale latest-event cursor');

  for (const path of [
    `deliveryAttempts.do?op=detail&shipmentId=${shipmentA.shipmentId}`,
    'deliveryAttempts.do?op=list&status=FAILED&warehouseId=21',
    `deliveryAttempts.do?op=list&status=RESCHEDULED&dueOnOrBefore=${ids.tomorrow}`
  ]) {
    const report = await warehouse.get(path);
    assertPage(report);
  }
  ctx.dispatch = {
    manifestId, shipmentIds: [shipmentA.shipmentId, shipmentB.shipmentId],
    deliveryAttemptIds: [failedId, rescheduledId, correctedId]
  };
}

async function runOperationsReportsAcceptance(baseUrl, ids) {
  const sales = new HttpClient(baseUrl);
  const warehouse = new HttpClient(baseUrl);
  await sales.login('sales');
  await warehouse.login('warehouse');
  const reportPaths = [
    [sales, `operationsReports.do?op=sales&dimension=CUSTOMER&from=${ids.today}&to=${ids.today}&customerId=${ids.customerId}`],
    [sales, `operationsReports.do?op=sales&dimension=PRODUCT&from=${ids.today}&to=${ids.today}&productId=${ids.productId}`],
    [warehouse, `operationsReports.do?op=backlog&from=${ids.today}&to=${ids.future}&customerId=${ids.customerId}`],
    [warehouse, `operationsReports.do?op=supplierQuality&from=${dayOffset(-7)}&to=${ids.today}&supplierId=41&exceptionsOnly=true`],
    [warehouse, `operationsReports.do?op=stockActivity&from=${ids.today}&to=${ids.today}&warehouseId=21&productId=${ids.productId}&status=RECEIPT`]
  ];
  for (const [client, path] of reportPaths) {
    const page = await client.get(path);
    assertPage(page);
    for (const form of forms(page.body)) {
      const action = new URL(form.action, page.url);
      if (action.pathname.endsWith('/operationsReports.do')) {
        assert.equal(form.method, 'GET', `Operations report filter must remain read-only at ${path}`);
      }
    }
  }
  const deniedSales = await warehouse.get(`operationsReports.do?op=sales&dimension=CUSTOMER&from=${ids.today}&to=${ids.today}`);
  assert.equal(deniedSales.status, 403, 'Warehouse role must not read sales summary reports');
  const deniedPost = await sales.request('operationsReports.do', {
    method: 'POST',
    fields: new URLSearchParams({ op: 'sales', dimension: 'CUSTOMER', from: ids.today, to: ids.today }),
    follow: false
  });
  assertStatus(deniedPost, [403, 405], 'Operations reports must not expose state-changing POST handling');
}

export async function runExtensionAcceptanceFlow(baseUrl, selected = ['quotes', 'amendments', 'dispatch', 'reports']) {
  assert.ok(selected.length > 0 && selected.every(group => ['quotes', 'amendments', 'dispatch', 'reports'].includes(group)),
    'Unknown extension test selector');
  const ctx = {
    baseUrl,
    key: `EX${Date.now().toString(36)}${randomBytes(3).toString('hex')}`.toUpperCase(),
    at: new Date().toISOString(),
    selected,
    checks: []
  };
  async function check(name, action) {
    try {
      await action();
      ctx.checks.push({ name, result: 'PASS' });
      console.log(`PASS HTTP extension ${name}`);
    } catch (error) {
      ctx.checks.push({ name, result: 'FAIL', error: error.message });
      throw error;
    }
  }
  try {
    await check('UI route contract preflight', () => ensureExtensionSurface(baseUrl, ctx, selected));
    const admin = new HttpClient(baseUrl);
    const warehouse = new HttpClient(baseUrl);
    await admin.login('admin');
    await warehouse.login('warehouse');
    const ids = await createMasterData(admin, warehouse, ctx.key);
    ctx.fixture = ids;
    if (selected.includes('quotes')) await check('quotation approval acceptance conversion idempotency', () => runQuotationAcceptance(baseUrl, ids, ctx));
    if (selected.includes('amendments')) await check('order amendment independent approval and reservation release', () => runOrderAmendmentAcceptance(baseUrl, ids, ctx));
    if (selected.includes('dispatch')) await check('dispatch manifest shared confirmation and delivery corrections', () => runDispatchAndDeliveryAcceptance(baseUrl, ids, ctx));
    if (selected.includes('reports')) await check('read-only operations report filters and permissions', () => runOperationsReportsAcceptance(baseUrl, ids));
    ctx.result = 'PASS';
    return ctx;
  } catch (error) {
    ctx.result = 'FAIL';
    ctx.error = error.message;
    throw error;
  } finally {
    await mkdir('.runtime/smoke', { recursive: true });
    await writeFile('.runtime/smoke/http-extension-results.json', JSON.stringify(ctx, null, 2), 'utf8');
  }
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  runExtensionAcceptanceFlow(process.argv[2] || 'http://127.0.0.1:18080/wholesale',
    process.argv[3] ? process.argv[3].split(',') : undefined).catch(error => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
