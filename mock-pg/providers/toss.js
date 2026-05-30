'use strict';

function buildReadyResponse(tid, host) {
  return {
    paymentKey: tid,
    checkoutPage: `http://${host}/pg/toss/checkout?tid=${tid}`,
    orderId: null,
    status: 'READY',
    requestedAt: new Date().toISOString(),
  };
}

function buildApproveResponse(transaction) {
  return {
    paymentKey: transaction.tid,
    orderId: transaction.partnerOrderId,
    orderName: transaction.itemName,
    status: 'DONE',
    method: '카드',
    totalAmount: transaction.amount,
    balanceAmount: transaction.amount,
    suppliedAmount: Math.floor(transaction.amount / 1.1),
    vat: transaction.amount - Math.floor(transaction.amount / 1.1),
    approvedAt: new Date().toISOString(),
    card: {
      amount: transaction.amount,
      company: '국민',
      number: '123456789012',
      installmentPlanMonths: 0,
      approveNo: `MOCK${transaction.tid.slice(-6)}`,
    },
  };
}

function buildCancelResponse(transaction) {
  return {
    paymentKey: transaction.tid,
    status: 'CANCELED',
    cancels: [
      {
        cancelAmount: transaction.amount,
        cancelReason: '테스트 취소',
        canceledAt: new Date().toISOString(),
      },
    ],
  };
}

module.exports = { buildReadyResponse, buildApproveResponse, buildCancelResponse };
