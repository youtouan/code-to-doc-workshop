// Characterization tests for the order CSV import (wholesale-batch, import-orders).
// They pin what the legacy system does today at the batch boundary: a CSV goes in; the stored row results,
// the created orders and the process exit code come out. Write what the system returns today, not what it
// should return. Never change legacy code to make a test pass.
//
// How to run (in the lab, from the repository root): node --test "tests/characterization/**/*.test.mjs"
// How the legacy system is started and reset: support/order-import.mjs runs scripts/observe-import.sh, which
//   re-creates its own database from database/*.sql and runs import-orders once (10 to 20 seconds).
//   The app database and the web app are not touched.
// Normalization: none. Timestamps, generated IDs and order numbers are not compared.
import { before, test } from 'node:test';
import assert from 'node:assert/strict';
import { importOrders, ordersCreatedFor, rowFor } from './support/order-import.mjs';

let result;

before(() => {
  result = importOrders('samples/orders-rejections.csv');
});

// Worked example for a different rule: header fields that differ within one external_key group.
test('CHAR-ORD-002 a group whose customer_code differs between its rows is rejected as a whole', () => {
  const row = rowFor(result, 'CSV-ORDER-HEADER');
  assert.equal(row.result, 'REJECTED');
  assert.equal(row.code, 'orderImport.headerConflict');
  assert.equal(row.sourceRows, '3..4'); // one result covers both data rows of the group
  assert.equal(ordersCreatedFor(result, 'CSV-ORDER-HEADER'), 0);
  assert.equal(result.exitCode, 3);
  // The message wording is not pinned: only the code, the rows, the missing order and the exit code are.
});

// 2-1: 重複した商品のグループ（CSV-ORDER-DUPLICATE）の結果を固定するテストを、この下に足す。
//      テスト名は 2-1 の指示どおりの ID で始める。上の例と同じく、メッセージの文言は固定しない。
//      テストを足したら、次の todo の行は消してよい。
test.todo('2-1: duplicate product group (CSV-ORDER-DUPLICATE) is not pinned yet');
