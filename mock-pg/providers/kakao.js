'use strict';

function buildReadyResponse(tid, host) {
  return {
    tid,
    next_redirect_mobile_url: `http://${host}/pg/kakao/checkout?tid=${tid}`,
    next_redirect_pc_url: `http://${host}/pg/kakao/checkout?tid=${tid}`,
    created_at: new Date().toISOString(),
  };
}

function buildApproveResponse(transaction) {
  return {
    tid: transaction.tid,
    aid: `A${transaction.tid}`,
    cid: transaction.partnerOrderId,
    status: 'SUCCESS',
    partner_order_id: transaction.partnerOrderId,
    partner_user_id: transaction.partnerUserId,
    payment_method_type: 'MONEY',
    amount: {
      total: transaction.amount,
      tax_free: 0,
      vat: Math.floor(transaction.amount / 11),
    },
    item_name: transaction.itemName,
    approved_at: new Date().toISOString(),
  };
}

function buildCancelResponse(transaction) {
  return {
    tid: transaction.tid,
    status: 'CANCEL_PAYMENT',
    canceled_at: new Date().toISOString(),
    canceled_amount: { total: transaction.amount },
  };
}

module.exports = { buildReadyResponse, buildApproveResponse, buildCancelResponse };
