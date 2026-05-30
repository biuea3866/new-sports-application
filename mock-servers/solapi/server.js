'use strict';

// SOLAPI(솔라피) 단건 발송 API mock.
// 실 API: POST https://api.solapi.com/messages/v4/send
//   body: { "message": { "to": "...", "from": "...", "text": "..." } }
// 응답 스키마(groupId/messageId/statusCode)를 모사합니다.
// 실발송은 발신번호 사전등록(사업자)이 필요하므로 본 mock 으로 대체합니다.

const express = require('express');

const app = express();
const PORT = process.env.PORT || 8080;

app.use(express.json());

function randomId(prefix) {
  return `${prefix}${Date.now().toString(36).toUpperCase()}${Math.floor(Math.random() * 1e6)}`;
}

app.post('/messages/v4/send', (req, res) => {
  const message = (req.body && req.body.message) || {};
  console.log(`[solapi] send to=${message.to} from=${message.from} text="${(message.text || '').slice(0, 40)}"`);

  if (!message.to || !message.text) {
    return res.status(400).json({
      errorCode: 'ValidationError',
      errorMessage: 'message.to and message.text are required',
    });
  }

  return res.json({
    groupId: randomId('G4V'),
    messageId: randomId('M4V'),
    to: message.to,
    from: message.from || '0700000000',
    type: 'SMS',
    statusCode: '2000',
    statusMessage: '정상 접수(이통사로 접수 예정)',
    accountId: 'MOCK0000000000',
  });
});

app.get('/health', (_req, res) => res.json({ status: 'UP' }));

app.listen(PORT, () => {
  console.log(`[solapi] mock listening on :${PORT}`);
});
