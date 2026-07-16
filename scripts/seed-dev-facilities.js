// dev 데모용 시설 시드 (MongoDB). 재실행 가능 — SEED- 코드 범위만 정리 후 재삽입.
// 실행: scripts/seed-dev.sh (권장) 또는
//   docker exec -i sports-dev-mongodb-1 mongosh sports < scripts/seed-dev-facilities.js
// 시설 위치는 GeoJSON Point(2dsphere 인덱스)로 저장해야 /facilities/near 지리 검색이 매칭된다.
db.facilities.deleteMany({ code: /^SEED-/ });

const districts = [
  { gu: '강남구', lat: 37.4979, lng: 127.0276 },
  { gu: '서초구', lat: 37.4837, lng: 127.0324 },
  { gu: '송파구', lat: 37.5145, lng: 127.1059 },
  { gu: '마포구', lat: 37.5638, lng: 126.9084 },
  { gu: '용산구', lat: 37.5326, lng: 126.9905 },
  { gu: '성동구', lat: 37.5634, lng: 127.0369 },
  { gu: '영등포구', lat: 37.5264, lng: 126.8962 },
  { gu: '관악구', lat: 37.4784, lng: 126.9516 },
];
// 전국 주요 도시(실제 중심 좌표) — 지도에 전국이 채워지도록. near 검색은 위 서울 밀집분이 커버.
const nationwide = [
  { sido: '서울특별시', gu: '노원구', lat: 37.6542, lng: 127.0568, code: '11350' },
  { sido: '서울특별시', gu: '강서구', lat: 37.5509, lng: 126.8495, code: '11500' },
  { sido: '부산광역시', gu: '해운대구', lat: 35.1631, lng: 129.1636, code: '26350' },
  { sido: '부산광역시', gu: '수영구', lat: 35.1455, lng: 129.1132, code: '26500' },
  { sido: '부산광역시', gu: '동래구', lat: 35.2049, lng: 129.0837, code: '26260' },
  { sido: '대구광역시', gu: '수성구', lat: 35.8583, lng: 128.6306, code: '27260' },
  { sido: '대구광역시', gu: '중구', lat: 35.8693, lng: 128.6062, code: '27110' },
  { sido: '인천광역시', gu: '남동구', lat: 37.4467, lng: 126.7314, code: '28200' },
  { sido: '인천광역시', gu: '연수구', lat: 37.4103, lng: 126.6784, code: '28185' },
  { sido: '광주광역시', gu: '서구', lat: 35.1517, lng: 126.8901, code: '29140' },
  { sido: '광주광역시', gu: '북구', lat: 35.1740, lng: 126.9120, code: '29170' },
  { sido: '대전광역시', gu: '유성구', lat: 36.3623, lng: 127.3562, code: '30200' },
  { sido: '대전광역시', gu: '서구', lat: 36.3552, lng: 127.3838, code: '30170' },
  { sido: '울산광역시', gu: '남구', lat: 35.5439, lng: 129.3300, code: '31140' },
  { sido: '경기도', gu: '수원시 영통구', lat: 37.2595, lng: 127.0467, code: '41117' },
  { sido: '경기도', gu: '성남시 분당구', lat: 37.3826, lng: 127.1189, code: '41135' },
  { sido: '경기도', gu: '고양시 일산동구', lat: 37.6584, lng: 126.7746, code: '41285' },
  { sido: '강원특별자치도', gu: '춘천시', lat: 37.8813, lng: 127.7300, code: '51110' },
  { sido: '강원특별자치도', gu: '원주시', lat: 37.3422, lng: 127.9202, code: '51130' },
  { sido: '전북특별자치도', gu: '전주시 완산구', lat: 35.8114, lng: 127.1190, code: '52111' },
  { sido: '경상남도', gu: '창원시 성산구', lat: 35.2280, lng: 128.6811, code: '48123' },
  { sido: '경상남도', gu: '김해시', lat: 35.2285, lng: 128.8894, code: '48250' },
  { sido: '충청남도', gu: '천안시 서북구', lat: 36.8535, lng: 127.1123, code: '44133' },
  { sido: '제주특별자치도', gu: '제주시', lat: 33.4996, lng: 126.5312, code: '50110' },
];
const types = ['수영장', '축구장', '테니스장', '농구장', '배드민턴장', '헬스장', '골프연습장'];

const docs = [];
let n = 0;

// 1) 서울 밀집분 — near 검색 데모용
for (const d of districts) {
  for (let t = 0; t < types.length; t++) {
    n++;
    const dlat = (((n * 37) % 16) - 8) * 0.001; // 구 중심에서 ±700m 흩뿌림
    const dlng = (((n * 53) % 16) - 8) * 0.001;
    docs.push({
      code: 'SEED-' + String(n).padStart(4, '0'),
      name: '[SEED] ' + d.gu + ' ' + types[t],
      gu: d.gu,
      type: types[t],
      address: '서울특별시 ' + d.gu + ' 테스트로 ' + n,
      location: { type: 'Point', coordinates: [d.lng + dlng, d.lat + dlat] },
      parking: n % 2 === 0,
      tel: '02-000-' + String(1000 + n),
      home_page: '',
      edu_yn: n % 3 === 0,
      meta: {},
      sido_code: '11',
      sido_name: '서울특별시',
      sigungu_name: d.gu,
      sigungu_code: '11' + String(680 + t),
      _class: 'com.sportsapp.domain.facility.entity.Facility',
    });
  }
}

// 2) 전국 분산분 — 지도가 전국적으로 채워지도록 각 도시당 시설 여러 개
for (const region of nationwide) {
  for (let k = 0; k < 12; k++) {
    n++;
    const type = types[n % types.length];
    const dlat = (((n * 41) % 20) - 10) * 0.002; // 도시 중심에서 ±2km 흩뿌림
    const dlng = (((n * 59) % 20) - 10) * 0.002;
    docs.push({
      code: 'SEED-' + String(n).padStart(4, '0'),
      name: '[SEED] ' + region.gu + ' ' + type + ' ' + (k + 1) + '호',
      gu: region.gu,
      type: type,
      address: region.sido + ' ' + region.gu + ' 스포츠로 ' + n,
      location: { type: 'Point', coordinates: [region.lng + dlng, region.lat + dlat] },
      parking: n % 2 === 0,
      tel: '0' + (region.code.slice(0, 2)) + '-000-' + String(1000 + (n % 9000)),
      home_page: '',
      edu_yn: n % 3 === 0,
      meta: {},
      sido_code: region.code.slice(0, 2),
      sido_name: region.sido,
      sigungu_name: region.gu,
      sigungu_code: region.code,
      _class: 'com.sportsapp.domain.facility.entity.Facility',
    });
  }
}

db.facilities.insertMany(docs);
print('시설 시드: ' + db.facilities.countDocuments({ code: /^SEED-/ }) + '건 (서울 밀집 + 전국 분산)');
