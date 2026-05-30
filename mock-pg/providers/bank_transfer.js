'use strict';

const VIRTUAL_BANKS = ['국민은행', '신한은행', '우리은행', '하나은행', '기업은행'];

function randomAccountNumber() {
  return Array.from({ length: 13 }, () => Math.floor(Math.random() * 10)).join('');
}

function buildReadyResponse(tid, host) {
  const bankName = VIRTUAL_BANKS[Math.floor(Math.random() * VIRTUAL_BANKS.length)];
  const accountNumber = randomAccountNumber();
  const dueDate = new Date(Date.now() + 24 * 60 * 60 * 1000).toISOString();

  return {
    tid,
    status: 'PENDING_DEPOSIT',
    checkoutPage: `http://${host}/pg/bank_transfer/checkout?tid=${tid}`,
    virtualAccount: {
      bankName,
      accountNumber,
      holderName: '스포츠앱(주)',
      dueDate,
    },
  };
}

function buildApproveResponse(transaction) {
  return {
    tid: transaction.tid,
    orderId: transaction.partnerOrderId,
    status: 'DEPOSIT_CONFIRMED',
    amount: transaction.amount,
    bankName: transaction.virtualAccount.bankName,
    accountNumber: transaction.virtualAccount.accountNumber,
    depositedAt: new Date().toISOString(),
  };
}

function buildCancelResponse(transaction) {
  return {
    tid: transaction.tid,
    status: 'REFUND_REQUESTED',
    cancelAmount: transaction.amount,
    canceledAt: new Date().toISOString(),
  };
}

module.exports = { buildReadyResponse, buildApproveResponse, buildCancelResponse };
