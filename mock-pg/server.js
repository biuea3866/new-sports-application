'use strict';

const express = require('express');
const http = require('http');
const { randomUUID } = require('crypto');
const path = require('path');

const PORT = process.env.PORT || 9090;
const BE_WEBHOOK_URL = process.env.BE_WEBHOOK_URL || 'http://localhost:8080/payments/webhook';

const PROVIDERS = {
  kakao: require('./providers/kakao'),
  toss: require('./providers/toss'),
  naver: require('./providers/naver'),
  danal: require('./providers/danal'),
  bank_transfer: require('./providers/bank_transfer'),
  card: require('./providers/card'),
};

// in-memory transaction store
// key: tid, value: { tid, provider, status, partnerOrderId, partnerUserId, amount, itemName, returnUrl, failUrl, webhookSentAt, virtualAccount? }
const transactions = new Map();

const app = express();
app.use(express.json());
app.use(express.urlencoded({ extended: true }));
app.use(express.static(path.join(__dirname, 'public')));

// ──────────────────────────────────────────────
// Middleware: validate provider
// ──────────────────────────────────────────────
function requireProvider(req, res, next) {
  const { provider } = req.params;
  if (!PROVIDERS[provider]) {
    return res.status(404).json({ error: `Unknown provider: ${provider}` });
  }
  next();
}

// ──────────────────────────────────────────────
// POST /pg/:provider/ready
// Body: { partner_order_id, partner_user_id, item_name, amount, return_url, fail_url }
// ──────────────────────────────────────────────
app.post('/pg/:provider/ready', requireProvider, (req, res) => {
  const { provider } = req.params;
  const { partner_order_id, partner_user_id, item_name, amount, return_url, fail_url } = req.body;

  if (!partner_order_id || !amount) {
    return res.status(400).json({ error: 'partner_order_id and amount are required' });
  }

  const tid = `MOCK_${provider.toUpperCase()}_${randomUUID().replace(/-/g, '').slice(0, 16)}`;
  const host = req.get('host');

  const transaction = {
    tid,
    provider,
    status: provider === 'bank_transfer' ? 'PENDING_DEPOSIT' : 'READY',
    partnerOrderId: partner_order_id,
    partnerUserId: partner_user_id || 'anonymous',
    itemName: item_name || '상품',
    amount: Number(amount),
    returnUrl: return_url || '',
    failUrl: fail_url || '',
    createdAt: new Date().toISOString(),
    webhookSentAt: null,
  };

  const providerResponse = PROVIDERS[provider].buildReadyResponse(tid, host);

  // bank_transfer: attach virtual account info
  if (provider === 'bank_transfer' && providerResponse.virtualAccount) {
    transaction.virtualAccount = providerResponse.virtualAccount;
  }

  transactions.set(tid, transaction);

  console.log(`[ready] provider=${provider} tid=${tid} amount=${amount}`);
  res.json(providerResponse);
});

// ──────────────────────────────────────────────
// GET /pg/:provider/checkout?tid=...
// Returns HTML checkout page
// ──────────────────────────────────────────────
app.get('/pg/:provider/checkout', requireProvider, (req, res) => {
  const { provider } = req.params;
  const { tid } = req.query;

  const tx = transactions.get(tid);
  if (!tx) {
    return res.status(404).send('<h1>거래를 찾을 수 없습니다</h1>');
  }

  const isBankTransfer = provider === 'bank_transfer';
  const bankInfo = isBankTransfer && tx.virtualAccount
    ? `<p>입금 계좌: <strong>${tx.virtualAccount.bankName} ${tx.virtualAccount.accountNumber}</strong> (예금주: ${tx.virtualAccount.holderName})</p>
       <p>입금 기한: ${tx.virtualAccount.dueDate}</p>`
    : '';

  const html = `<!DOCTYPE html>
<html lang="ko">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>Mock PG 결제창 — ${provider}</title>
  <style>
    body { font-family: sans-serif; max-width: 480px; margin: 40px auto; padding: 20px; }
    .card { border: 1px solid #ddd; border-radius: 8px; padding: 24px; }
    h2 { margin-top: 0; }
    .amount { font-size: 1.5em; font-weight: bold; color: #333; }
    .actions { margin-top: 24px; display: flex; gap: 12px; }
    button { flex: 1; padding: 12px; font-size: 1em; border: none; border-radius: 6px; cursor: pointer; }
    .btn-approve { background: #4CAF50; color: white; }
    .btn-cancel  { background: #f44336; color: white; }
    .provider-badge { display: inline-block; background: #eee; border-radius: 4px; padding: 2px 8px; font-size: 0.8em; }
  </style>
</head>
<body>
  <div class="card">
    <span class="provider-badge">${provider.toUpperCase()}</span>
    <h2>결제 확인</h2>
    <p>주문번호: <code>${tx.partnerOrderId}</code></p>
    <p>상품명: ${tx.itemName}</p>
    <p class="amount">${tx.amount.toLocaleString()}원</p>
    ${bankInfo}
    <div class="actions">
      <form method="POST" action="/pg/${provider}/approve" style="flex:1">
        <input type="hidden" name="tid" value="${tid}">
        <button type="submit" class="btn-approve">${isBankTransfer ? '입금 확인' : '결제 승인'}</button>
      </form>
      <form method="POST" action="/pg/${provider}/cancel" style="flex:1">
        <input type="hidden" name="tid" value="${tid}">
        <button type="submit" class="btn-cancel">취소</button>
      </form>
    </div>
  </div>
</body>
</html>`;

  res.send(html);
});

// ──────────────────────────────────────────────
// POST /pg/:provider/approve
// Body: { tid }
// Idempotent: same tid second call returns 200 without re-sending webhook
// ──────────────────────────────────────────────
app.post('/pg/:provider/approve', requireProvider, async (req, res) => {
  const { provider } = req.params;
  const { tid } = req.body;

  if (!tid) {
    return res.status(400).json({ error: 'tid is required' });
  }

  const tx = transactions.get(tid);
  if (!tx) {
    return res.status(404).json({ error: 'Transaction not found' });
  }
  if (tx.provider !== provider) {
    return res.status(400).json({ error: 'Provider mismatch' });
  }

  // Idempotency: already approved
  if (tx.status === 'APPROVED' || tx.status === 'DEPOSIT_CONFIRMED') {
    console.log(`[approve] idempotent skip tid=${tid}`);
    return res.json({ ...PROVIDERS[provider].buildApproveResponse(tx), idempotent: true });
  }

  if (tx.status === 'CANCELED') {
    return res.status(400).json({ error: 'Transaction is already canceled' });
  }

  tx.status = provider === 'bank_transfer' ? 'DEPOSIT_CONFIRMED' : 'APPROVED';
  tx.approvedAt = new Date().toISOString();

  const approveResponse = PROVIDERS[provider].buildApproveResponse(tx);

  // Send webhook to BE
  await sendWebhook('PAYMENT_APPROVED', tx, approveResponse);

  console.log(`[approve] provider=${provider} tid=${tid}`);

  // If form POST (browser), redirect to returnUrl
  if (req.get('content-type')?.includes('application/x-www-form-urlencoded') && tx.returnUrl) {
    return res.redirect(`${tx.returnUrl}?tid=${tid}&status=approved`);
  }

  res.json(approveResponse);
});

// ──────────────────────────────────────────────
// POST /pg/:provider/cancel
// Body: { tid }
// ──────────────────────────────────────────────
app.post('/pg/:provider/cancel', requireProvider, async (req, res) => {
  const { provider } = req.params;
  const { tid } = req.body;

  if (!tid) {
    return res.status(400).json({ error: 'tid is required' });
  }

  const tx = transactions.get(tid);
  if (!tx) {
    return res.status(404).json({ error: 'Transaction not found' });
  }
  if (tx.provider !== provider) {
    return res.status(400).json({ error: 'Provider mismatch' });
  }

  // Idempotency: already canceled
  if (tx.status === 'CANCELED') {
    console.log(`[cancel] idempotent skip tid=${tid}`);
    return res.json({ ...PROVIDERS[provider].buildCancelResponse(tx), idempotent: true });
  }

  tx.status = 'CANCELED';
  tx.canceledAt = new Date().toISOString();

  const cancelResponse = PROVIDERS[provider].buildCancelResponse(tx);

  // Send cancel webhook to BE
  await sendWebhook('PAYMENT_CANCELED', tx, cancelResponse);

  console.log(`[cancel] provider=${provider} tid=${tid}`);

  // If form POST (browser), redirect to failUrl or returnUrl
  if (req.get('content-type')?.includes('application/x-www-form-urlencoded')) {
    const redirectTarget = tx.failUrl || tx.returnUrl;
    if (redirectTarget) {
      return res.redirect(`${redirectTarget}?tid=${tid}&status=canceled`);
    }
  }

  res.json(cancelResponse);
});

// ──────────────────────────────────────────────
// GET /pg/transactions (debug — dev only)
// ──────────────────────────────────────────────
app.get('/pg/transactions', (req, res) => {
  res.json(Array.from(transactions.values()));
});

// ──────────────────────────────────────────────
// Webhook sender
// ──────────────────────────────────────────────
async function sendWebhook(eventType, tx, payload) {
  const body = JSON.stringify({
    eventType,
    provider: tx.provider,
    tid: tx.tid,
    orderId: tx.partnerOrderId,
    amount: tx.amount,
    status: tx.status,
    timestamp: new Date().toISOString(),
    providerPayload: payload,
  });

  const webhookUrl = new URL(BE_WEBHOOK_URL);
  const options = {
    hostname: webhookUrl.hostname,
    port: webhookUrl.port || 80,
    path: webhookUrl.pathname,
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Content-Length': Buffer.byteLength(body),
      'X-Mock-PG-Signature': `mock_sig_${tx.tid}`,
    },
  };

  return new Promise((resolve) => {
    const req = http.request(options, (res) => {
      tx.webhookSentAt = new Date().toISOString();
      console.log(`[webhook] ${eventType} tid=${tx.tid} → ${BE_WEBHOOK_URL} status=${res.statusCode}`);
      resolve();
    });

    req.on('error', (err) => {
      console.warn(`[webhook] ${eventType} tid=${tx.tid} → ${BE_WEBHOOK_URL} FAILED: ${err.message}`);
      resolve();
    });

    req.setTimeout(3000, () => {
      console.warn(`[webhook] ${eventType} tid=${tx.tid} → ${BE_WEBHOOK_URL} TIMEOUT`);
      req.destroy();
      resolve();
    });

    req.write(body);
    req.end();
  });
}

// ──────────────────────────────────────────────
// Start server (only when run directly, not when require()'d in tests)
// ──────────────────────────────────────────────
if (require.main === module) {
  app.listen(PORT, () => {
    console.log(`[mock-pg] Server running on http://localhost:${PORT}`);
    console.log(`[mock-pg] BE_WEBHOOK_URL = ${BE_WEBHOOK_URL}`);
    console.log(`[mock-pg] Providers: ${Object.keys(PROVIDERS).join(', ')}`);
  });
}

module.exports = { app, transactions };
