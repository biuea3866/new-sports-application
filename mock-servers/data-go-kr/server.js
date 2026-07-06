'use strict';

// 공공데이터포털(data.go.kr) mock — 세 가지 서비스 모사.
//
// 1) 전국 공공체육시설 목록
//    GET /openapi/service/publicSportsFacility/getList?serviceKey=&pageNo=&numOfRows=&dataType=JSON
//    응답: response.body.items.item[] (시설명/주소/위경도/외부ID)
//
// 2) 기상청 단기예보 (getVilageFcst)
//    실 endpoint: https://apis.data.go.kr/1360000/VilageFcstInfoService_2.0/getVilageFcst
//    → 본 mock 은 base-url 뒤 경로 /getVilageFcst 로 동일하게 받는다.
//    응답: response.body.items.item[] (category/fcstDate/fcstTime/fcstValue)
//
// 3) 에어코리아(한국환경공단) 대기질 3단계 체이닝
//    getTMStdrCrdnt(umdName/addr) → tmX,tmY
//    getNearbyMsrstnList(tmX,tmY) → stationName
//    getMsrstnAcctoRltmMesureDnsty(stationName) → pm10Value/pm25Value/dataTime
//    응답: response.body.items.item[] (다른 서비스와 동일 envelope)

const express = require('express');

const app = express();
const PORT = process.env.PORT || 8080;

// 서울(8) + 그 외 11개 시/도(22) = 30개 지역. TOTAL_FACILITIES(750) / 30 = 25 → 서울
// 외 지역이 정확히 550건(>=500) 적재되도록 나눠 떨어지는 값을 선택했다.
const REGIONS = [
  { sido: '서울특별시', gu: '강남구' },
  { sido: '서울특별시', gu: '송파구' },
  { sido: '서울특별시', gu: '마포구' },
  { sido: '서울특별시', gu: '성동구' },
  { sido: '서울특별시', gu: '용산구' },
  { sido: '서울특별시', gu: '서초구' },
  { sido: '서울특별시', gu: '노원구' },
  { sido: '서울특별시', gu: '관악구' },
  { sido: '부산광역시', gu: '해운대구' },
  { sido: '부산광역시', gu: '수영구' },
  { sido: '부산광역시', gu: '동래구' },
  { sido: '대구광역시', gu: '수성구' },
  { sido: '대구광역시', gu: '중구' },
  { sido: '인천광역시', gu: '남동구' },
  { sido: '인천광역시', gu: '연수구' },
  { sido: '광주광역시', gu: '서구' },
  { sido: '광주광역시', gu: '북구' },
  { sido: '대전광역시', gu: '유성구' },
  { sido: '대전광역시', gu: '서구' },
  { sido: '울산광역시', gu: '남구' },
  { sido: '울산광역시', gu: '중구' },
  { sido: '경기도', gu: '수원시 영통구' },
  { sido: '경기도', gu: '성남시 분당구' },
  { sido: '경기도', gu: '고양시 일산동구' },
  { sido: '강원특별자치도', gu: '춘천시' },
  { sido: '강원특별자치도', gu: '원주시' },
  { sido: '전북특별자치도', gu: '전주시 완산구' },
  { sido: '경상남도', gu: '창원시 성산구' },
  { sido: '경상남도', gu: '김해시' },
  { sido: '충청남도', gu: '천안시 서북구' },
];
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
  const region = REGIONS[seq % REGIONS.length];
  const type = SPORT_TYPES[seq % SPORT_TYPES.length];
  const lat = (33.1 + (seq % 500) / 100).toFixed(7);
  const lng = (126.1 + (seq % 700) / 100).toFixed(7);
  return {
    cpId: `PUB-${String(seq).padStart(5, '0')}`,
    facilNm: `${region.gu} 공공${type} ${seq}호`,
    roadAddr: `${region.sido} ${region.gu} 스포츠로 ${seq}`,
    la: lat,
    lo: lng,
    faciTy: type,
    gu: region.gu,
    telno: `02-${String(1000 + (seq % 9000)).padStart(4, '0')}-${String(seq % 10000).padStart(4, '0')}`,
  };
}

const TOTAL_FACILITIES = 750;

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

// --- 에어코리아(한국환경공단) 대기질 3단계 체이닝 mock ---

const FAIL_TRIGGER = '__FAIL__';
const STATION_LIST = [
  '서울 종로구', '서울 강남구', '서울 마포구',
  '부산 해운대구', '부산 수영구',
  '대구 수성구', '대구 중구',
  '인천 남동구', '인천 연수구',
  '광주 서구', '대전 유성구', '울산 남구',
  '수원 영통구', '성남 분당구', '고양 일산동구',
];

function hashCode(str) {
  let hash = 0;
  for (let i = 0; i < str.length; i += 1) {
    hash = (hash * 31 + str.charCodeAt(i)) | 0;
  }
  return Math.abs(hash);
}

// TM(중부원점) 좌표계 근사 범위로 umdName/addr 문자열을 결정적으로 매핑한다.
function tmCoordFor(key) {
  const hash = hashCode(key);
  const tmX = (150000 + (hash % 120000)).toFixed(2);
  const tmY = (350000 + (Math.floor(hash / 16) % 250000)).toFixed(2);
  return { tmX, tmY };
}

function formatDataTime(date) {
  const pad = (n) => String(n).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:00`;
}

app.get('/B552584/MsrstnInfoInqireSvc/getTMStdrCrdnt', (req, res) => {
  const umdName = (req.query.umdName || req.query.addr || '').toString();
  console.log(`[data-go-kr] getTMStdrCrdnt umdName=${umdName}`);

  const { tmX, tmY } = tmCoordFor(umdName || 'UNSPECIFIED');
  const payload = header(1, 1, 1);
  payload.response.body.items = { item: [{ umdName, tmX, tmY }] };
  return res.json(payload);
});

app.get('/B552584/MsrstnInfoInqireSvc/getNearbyMsrstnList', (req, res) => {
  const tmX = (req.query.tmX || '').toString();
  const tmY = (req.query.tmY || '').toString();
  console.log(`[data-go-kr] getNearbyMsrstnList tmX=${tmX} tmY=${tmY}`);

  const key = `${tmX}:${tmY}`;
  const hash = hashCode(key);
  const items = [
    { stationName: STATION_LIST[hash % STATION_LIST.length], tm: '0.32' },
    { stationName: STATION_LIST[(hash + 1) % STATION_LIST.length], tm: '1.14' },
  ];
  const payload = header(items.length, 1, items.length);
  payload.response.body.items = { item: items };
  return res.json(payload);
});

app.get('/B552584/ArpltnInforInqireSvc/getMsrstnAcctoRltmMesureDnsty', (req, res) => {
  const stationName = (req.query.stationName || '').toString();
  const ver = (req.query.ver || '').toString();
  console.log(`[data-go-kr] getMsrstnAcctoRltmMesureDnsty stationName=${stationName} ver=${ver}`);

  if (stationName === FAIL_TRIGGER) {
    return res.status(503).json({
      response: {
        header: { resultCode: '99', resultMsg: 'SERVICE_ERROR (mock forced failure)' },
      },
    });
  }

  const hash = hashCode(stationName || 'UNKNOWN');
  const pm10Value = String(20 + (hash % 130));
  const pm25Value = String(10 + (Math.floor(hash / 8) % 70));
  const dataTime = formatDataTime(new Date());

  // 실서버 실측(2026-07-06): ver 파라미터가 없으면 응답에 pm25Value 필드 자체가 없다.
  // ver=1.3 을 명시해야 pm25Value 가 포함된다. 로컬 mock 도 이 동작을 그대로 재현한다.
  const item = ver
    ? { stationName, pm10Value, pm25Value, dataTime }
    : { stationName, pm10Value, dataTime };

  const payload = header(1, 1, 1);
  payload.response.body.items = {
    item: [item],
  };
  return res.json(payload);
});

app.get('/health', (_req, res) => res.json({ status: 'UP' }));

app.listen(PORT, () => {
  console.log(`[data-go-kr] mock listening on :${PORT}`);
});
