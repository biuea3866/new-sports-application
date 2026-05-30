// =============================================================================
// QA E2E 회귀 시드 — MongoDB (database: sports, collection: facilities)
// =============================================================================
// 목적: facilities 가 MongoDB 에 저장되므로 seed.sql(MySQL) 로는 시드할 수 없다.
//       이 스크립트가 E2E-02-04 / E2E-02-05 케이스의 facilities 시드를 담당한다.
//
// 적용 대상 케이스:
//   E2E-02-04  시설 상세 조회      -> facilities 1건 이상 필요
//   E2E-02-05  시설 슬롯 목록      -> facilities + (MySQL) slots — facility_id 가 _id 와 일치
//
// _id 는 명시 문자열 (fac-001 ~ fac-003) — MySQL slots.facility_id 와 매칭된다.
// 도메인 모델: com.sportsapp.domain.facility.Facility (@Document collection="facilities")
//   - location 은 Spring Data MongoDB GeoJSON Point — {type:"Point", coordinates:[lng,lat]}
//   - audit 필드(createdAt/updatedAt 등)는 BaseDocument camelCase
//   - _class 는 Spring Data 가 역직렬화 시 사용하는 타입 판별자
//
// 멱등: replaceOne(upsert) — 반복 실행해도 동일 상태.
// 주입: docker exec -i qa-mongodb mongosh sports < qa/e2e/fixtures/seed-mongo.js
// =============================================================================

const FQCN = "com.sportsapp.domain.facility.Facility";
const now = new Date();

const facilities = [
  {
    _id: "fac-001",
    code: "GN-FUTSAL-001",
    name: "강남 스포츠센터 풋살장",
    gu: "강남구",
    type: "풋살장",
    address: "서울특별시 강남구 테헤란로 123",
    location: { type: "Point", coordinates: [127.0276, 37.4979] },
    parking: true,
    tel: "02-1234-5678",
    home_page: "https://gn-futsal.test.local",
    edu_yn: false,
    meta: { surface: "인조잔디", indoor: "true" },
    owner_user_id: NumberLong(3),
    createdAt: now,
    createdBy: NumberLong(3),
    updatedAt: now,
    updatedBy: NumberLong(3),
    deletedAt: null,
    deletedBy: null,
    _class: FQCN,
  },
  {
    _id: "fac-002",
    code: "GN-BASKET-002",
    name: "강남 실내 농구코트",
    gu: "강남구",
    type: "농구장",
    address: "서울특별시 강남구 봉은사로 456",
    location: { type: "Point", coordinates: [127.0586, 37.5145] },
    parking: false,
    tel: "02-2345-6789",
    home_page: "https://gn-basket.test.local",
    edu_yn: true,
    meta: { surface: "우레탄", indoor: "true" },
    owner_user_id: NumberLong(3),
    createdAt: now,
    createdBy: NumberLong(3),
    updatedAt: now,
    updatedBy: NumberLong(3),
    deletedAt: null,
    deletedBy: null,
    _class: FQCN,
  },
  {
    _id: "fac-003",
    code: "SC-FUTSAL-003",
    name: "송파 종합운동장 풋살장",
    gu: "송파구",
    type: "풋살장",
    address: "서울특별시 송파구 올림픽로 789",
    location: { type: "Point", coordinates: [127.1126, 37.5145] },
    parking: true,
    tel: "02-3456-7890",
    home_page: "https://sc-futsal.test.local",
    edu_yn: false,
    meta: { surface: "인조잔디", indoor: "false" },
    owner_user_id: NumberLong(3),
    createdAt: now,
    createdBy: NumberLong(3),
    updatedAt: now,
    updatedBy: NumberLong(3),
    deletedAt: null,
    deletedBy: null,
    _class: FQCN,
  },
];

for (const f of facilities) {
  db.facilities.replaceOne({ _id: f._id }, f, { upsert: true });
}

print("facilities upserted: " + db.facilities.countDocuments({ deletedAt: null }));
