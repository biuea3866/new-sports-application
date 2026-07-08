'use strict';

function buildReadyResponse(tid, host) {
  return {
    RETURNCODE: '0000',
    RETURNMSG: 'Success',
    CANCELURL: `http://${host}/pg/danal/checkout?tid=${tid}&action=cancel`,
    RETURNURL: `http://${host}/pg/danal/checkout?tid=${tid}`,
    TID: tid,
  };
}

function buildApproveResponse(transaction) {
  return {
    RETURNCODE: '0000',
    RETURNMSG: '정상처리',
    TID: transaction.tid,
    ORDERID: transaction.partnerOrderId,
    AMOUNT: String(transaction.amount),
    ITEMNAME: transaction.itemName,
    PAYMETHOD: 'CARD',
    CARDCODE: '381',
    CARDNAME: '신한카드',
    CARDNO: '411111******1111',
    AUTHCODE: `MOCK${transaction.tid.slice(-8)}`,
    TRANDATE: new Date().toISOString().slice(0, 10).replace(/-/g, ''),
    TRANTIME: new Date().toISOString().slice(11, 19).replace(/:/g, ''),
  };
}

function buildCancelResponse(transaction) {
  return {
    RETURNCODE: '0000',
    RETURNMSG: '취소완료',
    TID: transaction.tid,
    CANCELDATE: new Date().toISOString().slice(0, 10).replace(/-/g, ''),
    CANCELTIME: new Date().toISOString().slice(11, 19).replace(/:/g, ''),
  };
}

module.exports = { buildReadyResponse, buildApproveResponse, buildCancelResponse };
