import assert from 'node:assert/strict';
import { randomBytes } from 'node:crypto';
import { mkdir, writeFile } from 'node:fs/promises';
import { pathToFileURL } from 'node:url';
import { HttpClient, assertPage, decodeHtml, findForm, forms, links, suggestions, withFields } from './http-client.mjs';

function id(page) {
  const value = page.url.searchParams.get('id');
  assert.match(value || '', /^[1-9][0-9]*$/);
  return value;
}

function text(page) {
  return decodeHtml(page.body.replace(/<[^>]+>/g, ' ')).replace(/\s+/g, ' ');
}

function day(date) {
  const parts = new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Tokyo', year: 'numeric', month: '2-digit', day: '2-digit'
  }).formatToParts(date);
  const value = type => parts.find(part => part.type === type).value;
  return `${value('year')}-${value('month')}-${value('day')}`;
}

export async function runPayablesFlow(baseUrl) {
  const key = `AP${Date.now().toString(36)}${randomBytes(3).toString('hex')}`.toUpperCase();
  const warehouse = new HttpClient(baseUrl);
  const manager = new HttpClient(baseUrl);
  const billing = new HttpClient(baseUrl);
  await warehouse.login('warehouse');
  await manager.login('manager');
  await billing.login('billing');
  const today = day(new Date());
  const past = day(new Date(Date.now() - 7 * 86400000));
  const due = day(new Date(Date.now() + 30 * 86400000));
  let page = await warehouse.submit(await warehouse.get('purchases.do?op=new'), 'save', {
    supplierId: '41', warehouseId: '21', orderDate: past, expectedDate: today,
    productId: ['11'], quantity: ['10'], lineExpectedDate: [today], lineNote: ['AP test'],
    note: key
  });
  assertPage(page);
  const purchaseId = id(page);
  assertPage(await warehouse.submit(page, 'submit'));
  assertPage(await manager.submit(await manager.get(`purchases.do?op=detail&id=${purchaseId}`), 'approve'));
  page = await warehouse.get(`purchases.do?op=detail&id=${purchaseId}`);
  const receiveLink = links(page.body, page.url).find(link =>
    link.url.pathname.endsWith('/purchaseReceipts.do') && link.url.searchParams.get('op') === 'new');
  assert.ok(receiveLink);
  page = await warehouse.submit(await warehouse.get(receiveLink.url), 'receive', {
    requestKey: `${key}-receipt`, receiptDate: today, supplierDeliveryNumber: `${key}-delivery`,
    acceptedQuantity: ['8'], rejectedQuantity: ['2'], rejectionReason: ['破損'],
    lineNote: ['検品記録'], notes: 'Accepted goods only are eligible for AP matching'
  });
  assertPage(page);
  const receiptId = id(page);

  page = await billing.submit(await billing.get('apInvoices.do?op=new'), 'save', {
    supplierId: '41', supplierInvoiceNumber: `${key}-invoice`, invoiceDate: today, dueDate: due,
    productId: ['11'], description: ['単価差異の照合'], quantity: ['8'], unitPrice: ['325.00'],
    taxRate: ['0.1'], note: 'Invoice amount differs from the accepted receipt snapshot'
  });
  assertPage(page);
  const invoiceId = id(page);
  const matchLink = links(page.body, page.url).find(link => link.url.pathname.endsWith('/apMatches.do'));
  assert.ok(matchLink);
  let matching = await billing.get(matchLink.url);
  assertPage(matching);
  const choice = suggestions(matching.body).find(option => option.text.includes(`PR-${receiptId.padStart(8, '0')}`));
  assert.ok(choice, 'New accepted receipt must be offered as a matching source');
  const matchingForm = findForm(matching, 'save');
  const invoiceLine = matchingForm.controls.find(control => control.name === 'invoiceLineId')
    .options.find(option => option.value).value;
  const overmatch = await billing.submit(matching, 'save', {
    invoiceLineId: [invoiceLine], receiptLineId: [choice.value], quantity: ['9']
  });
  assert.equal(overmatch.status, 422, 'Rejected goods must not become matchable accepted quantities');
  matching = await billing.get(matchLink.url);
  page = await billing.submit(matching, 'save', {
    invoiceLineId: [invoiceLine], receiptLineId: [choice.value], quantity: ['8']
  });
  assertPage(page);
  page = await billing.get(`apInvoices.do?op=detail&id=${invoiceId}`);
  const cancellation = findForm(page, 'cancel');
  const unapprovedPost = await billing.request(new URL(cancellation.action, page.url), {
    method: 'POST', fields: withFields(cancellation, { op: 'post' }), follow: false
  });
  assert.equal(unapprovedPost.status, 422, 'Cost variance must be independently approved before posting');
  assertPage(await manager.submit(await manager.get(`apInvoices.do?op=detail&id=${invoiceId}`), 'approveVariance', {
    reason: '仕入先との単価差異を確認'
  }));
  page = await billing.submit(await billing.get(`apInvoices.do?op=detail&id=${invoiceId}`), 'post');
  assertPage(page);
  assert.match(text(page), /税込請求額\s+2,860\.00/);
  assert.match(text(page), /差異額 \/ 絶対差異合計\s+40\.00\s+\/\s+40\.00/);
  console.log('PASS HTTP purchase receipt -> bounded three-way matching -> independent variance approval -> AP posting');

  const paymentPage = await billing.get('apPayments.do?op=new&supplierId=41');
  page = await billing.submit(paymentPage, 'pay', {
    requestKey: `${key}-payment`, paymentDate: today, amount: '500.00', method: 'BANK_TRANSFER',
    reference: `${key}-bank`, invoiceId: [invoiceId], allocationAmount: ['500.00'], note: 'Manual payment'
  });
  assertPage(page);
  const paymentId = id(page);
  const duplicate = await billing.submit(paymentPage, 'pay', {
    requestKey: `${key}-payment`, paymentDate: today, amount: '500.00', method: 'BANK_TRANSFER',
    reference: `${key}-bank`, invoiceId: [invoiceId], allocationAmount: ['500.00'], note: 'Manual payment'
  });
  assertPage(duplicate);
  assert.equal(id(duplicate), paymentId);

  page = await billing.submit(await billing.get(`apCredits.do?op=new&invoiceId=${invoiceId}`), 'propose', {
    supplierCreditNumber: `${key}-credit`, creditDate: today,
    invoiceLineId: [invoiceLine], netAmount: ['100.00'], reason: '仕入先承認の財務値引'
  });
  assertPage(page);
  const creditId = id(page);
  page = await manager.get(`apCredits.do?op=detail&id=${creditId}`);
  const creditOperations = forms(page.body).filter(form => form.action.endsWith('/apCredits.do'));
  const operation = creditOperations.some(form => form.fields.get('op') === 'approve'
    || form.buttons.some(button => button.name === 'op' && button.value === 'approve')) ? 'approve' : 'post';
  assertPage(await manager.submit(page, operation, { reason: '独立承認済み' }));
  page = await billing.get(`apInvoices.do?op=detail&id=${invoiceId}`);
  assertPage(page);
  assert.match(text(page), /未払残高\s+2,250\.00\s+円/);
  assertPage(await billing.submit(await billing.get(`apPayments.do?op=detail&id=${paymentId}`), 'cancel', {
    reason: '銀行照合訂正のため支払全体を取消'
  }));
  page = await billing.get(`apInvoices.do?op=detail&id=${invoiceId}`);
  assertPage(page);
  assert.match(text(page), /未払残高\s+2,750\.00\s+円/);
  console.log('PASS HTTP supplier settlement, idempotent voucher, financial credit and complete payment reversal');
  await mkdir('.runtime/smoke', { recursive: true });
  await writeFile('.runtime/smoke/ap-results.json', JSON.stringify({
    key, purchaseId, receiptId, invoiceId, invoiceLine, paymentId, creditId, result: 'PASS'
  }, null, 2), 'utf8');
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  runPayablesFlow(process.argv[2] || 'http://127.0.0.1:18080/wholesale').catch(error => {
    console.error(error.message);
    process.exitCode = 1;
  });
}
