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
const types = ['수영장', '축구장', '테니스장', '농구장', '배드민턴장', '헬스장', '골프연습장'];

const docs = [];
let n = 0;
for (const d of districts) {
  for (let t = 0; t < types.length; t++) {
    n++;
    const dlat = (((n * 37) % 16) - 8) * 0.001; // 구 중심에서 ±700m 흩뿌림
    const dlng = (((n * 53) % 16) - 8) * 0.001;
    docs.push({
      code: 'SEED-' + String(n).padStart(3, '0'),
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
db.facilities.insertMany(docs);
print('시설 시드: ' + db.facilities.countDocuments({ code: /^SEED-/ }) + '건');
