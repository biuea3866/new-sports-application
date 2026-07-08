'use strict';

function buildReadyResponse(tid, host) {
  return {
    tid,
    status: 'READY',
    checkoutUrl: `http://${host}/pg/card/checkout?tid=${tid}`,
    createdAt: new Date().toISOString(),
  };
}

function buildApproveResponse(transaction) {
  return {
    tid: transaction.tid,
    orderId: transaction.partnerOrderId,
    status: 'APPROVED',
    method: 'CARD',
    amount: transaction.amount,
    cardInfo: {
      company: '현대카드',
      number: '4111-1111-1111-1111',
      installmentMonths: 0,
      approvalNumber: `MOCK${transaction.tid.slice(-8)}`,
    },
    approvedAt: new Date().toISOString(),
  };
}

function buildCancelResponse(transaction) {
  return {
    tid: transaction.tid,
    status: 'CANCELED',
    cancelAmount: transaction.amount,
    canceledAt: new Date().toISOString(),
  };
}

module.exports = { buildReadyResponse, buildApproveResponse, buildCancelResponse };
