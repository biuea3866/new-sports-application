'use strict';

// 공공데이터포털(data.go.kr) mock — 두 가지 서비스 모사.
//
// 1) 전국 공공체육시설 목록
//    GET /openapi/service/publicSportsFacility/getList?serviceKey=&pageNo=&numOfRows=&dataType=JSON
//    응답: response.body.items.item[] (시설명/주소/위경도/외부ID)
//
// 2) 기상청 단기예보 (getVilageFcst)
//    실 endpoint: https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst
//    → 본 mock 은 base-url 뒤 경로 /getVilageFcst 로 동일하게 받는다.
//    응답: response.body.items.item[] (category/fcstDate/fcstTime/fcstValue)

const express = require('express');

const app = express();
const PORT = process.env.PORT || 8080;

const GU_LIST = ['강남구', '송파구', '마포구', '성동구', '용산구', '서초구', '노원구', '관악구'];
const SPORT_TYPES = ['축구장', '농구장', '테니스장', '수영장', '배드민턴장', '풋살장'];

function header(totalCount, pageNo, numOfRows) {
  return {
    response: {
      header: { resultCode: '00', resultMsg: 'NORMAL SERVICE' },
      body: { pageNo, numOfRows, totalCount },
    },
  };
}

function buildFacility(seq) {
  const gu = GU_LIST[seq % GU_LIST.length];
  const type = SPORT_TYPES[seq % SPORT_TYPES.length];
  const lat = (37.45 + (seq % 50) / 1000).toFixed(7);
  const lng = (126.9 + (seq % 70) / 1000).toFixed(7);
  return {
    cpId: `PUB-${String(seq).padStart(5, '0')}`,
    facilNm: `${gu} 공공${type} ${seq}호`,
    roadAddr: `서울특별시 ${gu} 스포츠로 ${seq}`,
    la: lat,
    lo: lng,
    faciTy: type,
    gu,
    telno: `02-${String(1000 + (seq % 9000)).padStart(4, '0')}-${String(seq % 10000).padStart(4, '0')}`,
  };
}

const TOTAL_FACILITIES = 53;

app.get('/openapi/service/publicSportsFacility/getList', (req, res) => {
  const pageNo = parseInt(req.query.pageNo, 10) || 1;
  const numOfRows = parseInt(req.query.numOfRows, 10) || 10;
  console.log(`[data-go-kr] publicSportsFacility pageNo=${pageNo} numOfRows=${numOfRows} key=${(req.query.serviceKey || '').toString().slice(0, 8)}...`);

  const start = (pageNo - 1) * numOfRows;
  const items = [];
  for (let i = start; i < Math.min(start + numOfRows, TOTAL_FACILITIES); i += 1) {
    items.push(buildFacility(i + 1));
  }

  const payload = header(TOTAL_FACILITIES, pageNo, numOfRows);
  payload.response.body.items = { item: items };
  return res.json(payload);
});

const WEATHER_CATEGORIES = [
  { category: 'TMP', value: () => String(15 + Math.floor(Math.random() * 15)) },
  { category: 'SKY', value: () => String(1 + Math.floor(Math.random() * 4)) },
  { category: 'PTY', value: () => '0' },
  { category: 'POP', value: () => String(Math.floor(Math.random() * 100)) },
  { category: 'REH', value: () => String(40 + Math.floor(Math.random() * 50)) },
  { category: 'WSD', value: () => (1 + Math.random() * 5).toFixed(1) },
];

app.get('/getVilageFcst', (req, res) => {
  const nx = parseInt(req.query.nx, 10) || 60;
  const ny = parseInt(req.query.ny, 10) || 127;
  const baseDate = (req.query.base_date || '20260530').toString();
  console.log(`[data-go-kr] getVilageFcst nx=${nx} ny=${ny} base_date=${baseDate} key=${(req.query.serviceKey || '').toString().slice(0, 8)}...`);

  const items = [];
  const times = ['0900', '1200', '1500', '1800'];
  times.forEach((fcstTime) => {
    WEATHER_CATEGORIES.forEach((c) => {
      items.push({
        baseDate,
        baseTime: '0500',
        category: c.category,
        fcstDate: baseDate,
        fcstTime,
        fcstValue: c.value(),
        nx,
        ny,
      });
    });
  });

  const payload = header(items.length, 1, items.length);
  payload.response.body.items = { item: items };
  return res.json(payload);
});

app.get('/health', (_req, res) => res.json({ status: 'UP' }));

app.listen(PORT, () => {
  console.log(`[data-go-kr] mock listening on :${PORT}`);
});
