'use strict';

// Kakao Local REST API mock.
// 실 API: GET https://dapi.kakao.com/v2/local/search/address.json?query=<주소>
//   header: Authorization: KakaoAK <REST_API_KEY>
// 응답 스키마(documents[].x=경도, y=위도)를 그대로 모사합니다.
// 좌표는 query 문자열 해시로 서울 인근(37.4~37.7, 126.8~127.2)에 결정적으로 생성합니다.

const express = require('express');

const app = express();
const PORT = process.env.PORT || 8080;

function hash(str) {
  let h = 0;
  for (let i = 0; i < str.length; i += 1) {
    h = (h * 31 + str.charCodeAt(i)) | 0;
  }
  return Math.abs(h);
}

function coordsFor(query) {
  const h = hash(query);
  const lat = 37.4 + ((h % 3000) / 10000); // 37.4 ~ 37.7
  const lng = 126.8 + (((h >> 4) % 4000) / 10000); // 126.8 ~ 127.2
  return { lat: lat.toFixed(7), lng: lng.toFixed(7) };
}

app.get('/v2/local/search/address.json', (req, res) => {
  const query = (req.query.query || '').toString().trim();
  console.log(`[kakao-local] address search query="${query}" auth="${req.headers.authorization || ''}"`);

  if (!query) {
    return res.status(400).json({
      errorType: 'InvalidArgument',
      message: 'query parameter is required',
    });
  }

  const { lat, lng } = coordsFor(query);
  return res.json({
    meta: { total_count: 1, pageable_count: 1, is_end: true },
    documents: [
      {
        address_name: query,
        x: lng, // 경도(longitude)
        y: lat, // 위도(latitude)
        address_type: 'REGION_ADDR',
        address: { address_name: query, x: lng, y: lat },
        road_address: { address_name: query, x: lng, y: lat },
      },
    ],
  });
});

app.get('/health', (_req, res) => res.json({ status: 'UP' }));

app.listen(PORT, () => {
  console.log(`[kakao-local] mock listening on :${PORT}`);
});
