'use strict';

function buildReadyResponse(tid, host) {
  return {
    resultCode: 'Success',
    paymentId: tid,
    checkoutPageUrl: `http://${host}/pg/naver/checkout?tid=${tid}`,
  };
}

function buildApproveResponse(transaction) {
  return {
    resultCode: 'Success',
    paymentId: transaction.tid,
    orderId: transaction.partnerOrderId,
    totalPayAmount: transaction.amount,
    primaryPayAmount: transaction.amount,
    paymentMeans: 'CARD',
    cardNo: '4111-1111-1111-1111',
    cardCorpCode: 'SHINHAN',
    approvedAt: new Date().toISOString(),
  };
}

function buildCancelResponse(transaction) {
  return {
    resultCode: 'Success',
    paymentId: transaction.tid,
    cancelAmount: transaction.amount,
    canceledAt: new Date().toISOString(),
  };
}

module.exports = { buildReadyResponse, buildApproveResponse, buildCancelResponse };
