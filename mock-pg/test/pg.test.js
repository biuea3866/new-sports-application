'use strict';

const { test } = require('node:test');
const assert = require('node:assert/strict');

// Import app without starting the server
const { app, transactions } = require('../server');
const http = require('http');

let server;
let baseUrl;

function startServer() {
  return new Promise((resolve) => {
    server = http.createServer(app);
    server.listen(0, '127.0.0.1', () => {
      const { port } = server.address();
      baseUrl = `http://127.0.0.1:${port}`;
      resolve();
    });
  });
}

function stopServer() {
  return new Promise((resolve) => server.close(resolve));
}

async function request(method, path, body) {
  const url = new URL(path, baseUrl);
  const options = {
    method,
    headers: { 'Content-Type': 'application/json' },
  };
  return new Promise((resolve, reject) => {
    const req = http.request(url, options, (res) => {
      let data = '';
      res.on('data', (chunk) => { data += chunk; });
      res.on('end', () => {
        resolve({ status: res.statusCode, body: JSON.parse(data) });
      });
    });
    req.on('error', reject);
    if (body) req.write(JSON.stringify(body));
    req.end();
  });
}

// ──────────────────────────────────────────────
// Test: ready → approve flow (toss)
// ──────────────────────────────────────────────
test('ready → approve flow (toss)', async (t) => {
  await startServer();
  t.after(stopServer);
  transactions.clear();

  // 1. ready
  const readyRes = await request('POST', '/pg/toss/ready', {
    partner_order_id: 'ORDER-001',
    partner_user_id: 'USER-001',
    item_name: '수영장 이용권',
    amount: 30000,
    return_url: 'http://localhost:3000/payment/result',
  });

  assert.equal(readyRes.status, 200, 'ready should return 200');
  assert.ok(readyRes.body.paymentKey, 'toss ready should include paymentKey');
  assert.ok(readyRes.body.checkoutPage, 'toss ready should include checkoutPage');

  const tid = readyRes.body.paymentKey;

  // 2. approve
  const approveRes = await request('POST', '/pg/toss/approve', { tid });

  assert.equal(approveRes.status, 200, 'approve should return 200');
  assert.equal(approveRes.body.status, 'DONE', 'toss approve status should be DONE');
  assert.equal(approveRes.body.totalAmount, 30000, 'amount should match');
  assert.equal(approveRes.body.paymentKey, tid, 'paymentKey should match tid');

  // Verify in-memory status
  const tx = transactions.get(tid);
  assert.equal(tx.status, 'APPROVED', 'transaction status should be APPROVED');
});

// ──────────────────────────────────────────────
// Test: approve idempotency — same tid twice
// ──────────────────────────────────────────────
test('approve idempotency: same tid twice sends webhook only once', async (t) => {
  await startServer();
  t.after(stopServer);
  transactions.clear();

  const readyRes = await request('POST', '/pg/kakao/ready', {
    partner_order_id: 'ORDER-IDEMPOTENT',
    amount: 10000,
    item_name: '테스트 상품',
  });
  const tid = readyRes.body.tid;

  // First approve
  const first = await request('POST', '/pg/kakao/approve', { tid });
  assert.equal(first.status, 200);
  assert.equal(first.body.idempotent, undefined, 'first approve should not have idempotent flag');

  const firstWebhookSentAt = transactions.get(tid).webhookSentAt;

  // Second approve — idempotent
  const second = await request('POST', '/pg/kakao/approve', { tid });
  assert.equal(second.status, 200);
  assert.equal(second.body.idempotent, true, 'second approve should have idempotent=true');

  const secondWebhookSentAt = transactions.get(tid).webhookSentAt;

  // webhookSentAt should not change (no second webhook call)
  assert.equal(firstWebhookSentAt, secondWebhookSentAt, 'webhook should only be sent once');
});

// ──────────────────────────────────────────────
// Test: bank_transfer ready returns virtual account + PENDING_DEPOSIT
// ──────────────────────────────────────────────
test('bank_transfer ready returns virtual account with PENDING_DEPOSIT status', async (t) => {
  await startServer();
  t.after(stopServer);
  transactions.clear();

  const res = await request('POST', '/pg/bank_transfer/ready', {
    partner_order_id: 'ORDER-BANK-001',
    amount: 50000,
    item_name: '무통장 결제 테스트',
  });

  assert.equal(res.status, 200);
  assert.equal(res.body.status, 'PENDING_DEPOSIT');
  assert.ok(res.body.virtualAccount, 'should have virtualAccount');
  assert.ok(res.body.virtualAccount.bankName, 'should have bankName');
  assert.ok(res.body.virtualAccount.accountNumber, 'should have accountNumber');
  assert.ok(res.body.virtualAccount.dueDate, 'should have dueDate');
});

// ──────────────────────────────────────────────
// Test: cancel after approve returns error
// ──────────────────────────────────────────────
test('cancel on already-canceled transaction is idempotent', async (t) => {
  await startServer();
  t.after(stopServer);
  transactions.clear();

  const readyRes = await request('POST', '/pg/card/ready', {
    partner_order_id: 'ORDER-CANCEL',
    amount: 20000,
    item_name: '취소 테스트',
  });
  const tid = readyRes.body.tid;

  // First cancel
  const first = await request('POST', '/pg/card/cancel', { tid });
  assert.equal(first.status, 200);
  assert.equal(first.body.idempotent, undefined);

  // Second cancel — idempotent
  const second = await request('POST', '/pg/card/cancel', { tid });
  assert.equal(second.status, 200);
  assert.equal(second.body.idempotent, true);
});

// ──────────────────────────────────────────────
// Test: unknown provider returns 404
// ──────────────────────────────────────────────
test('unknown provider returns 404', async (t) => {
  await startServer();
  t.after(stopServer);

  const res = await request('POST', '/pg/unknown_provider/ready', {
    partner_order_id: 'X',
    amount: 1000,
  });
  assert.equal(res.status, 404);
});
