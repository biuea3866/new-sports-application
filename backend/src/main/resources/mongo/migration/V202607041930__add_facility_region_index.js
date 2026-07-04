// V202607041930__add_facility_region_index.js
//
// 목적: facilities 컬렉션에 시도/시군구/유형 복합 인덱스(idx_sido_sigungu_type) 추가.
// 근거: /Users/biuea/Desktop/dpdpdndn/프로젝트/스포츠앱/시설 전국 확장·대기질 연동/design-db.md §4.2, §4.3
//   - ESR 근거: sido_code/sigungu_code/type 전부 Equality 필터(Range/Sort 없음).
//     컬럼 순서는 필터 계층(시도 → 시군구 → 유형)과 일치시켜 {sido}, {sido,sigungu}, {sido,sigungu,type}
//     3가지 접두 조합 쿼리(F1~F3)를 단일 인덱스로 모두 커버한다.
//   - 기존 idx_gu_type({gu:1,type:1}), idx_code({code:1}), 2dsphere(location) 인덱스는 유지(하위 호환, expand 단계).
//   - validator는 이번 과제 범위 밖 — 신설하지 않는다(design-db §4.3).
//
// 실행: mongosh <connection-string> V202607041930__add_facility_region_index.js
// 멱등: createIndex는 동일 정의로 재실행해도 안전(no-op). _migrations 기록도 upsert로 재실행 안전.
//
// 롤백(역방향 스크립트):
//   db.facilities.dropIndex("idx_sido_sigungu_type");
//   db.getCollection("_migrations").deleteOne({ version: "V202607041930" });

(function () {
  const version = "V202607041930";
  const description = "add_facility_region_index";

  print(`[${version}] applying: ${description}`);

  // 1. 복합 인덱스 생성 (멱등 — 동일 정의면 재실행 시 no-op)
  db.facilities.createIndex(
    { sido_code: 1, sigungu_code: 1, type: 1 },
    { name: "idx_sido_sigungu_type", background: true }
  );

  // 2. 기존 인덱스 유지 확인용 로그 (건드리지 않음 — 존재 여부만 출력)
  const existingIndexNames = db.facilities.getIndexes().map((idx) => idx.name);
  print(`[${version}] facilities indexes after apply: ${existingIndexNames.join(", ")}`);

  // 3. 실행 이력 기록 (컨벤션 — 멱등 upsert)
  db.getCollection("_migrations").updateOne(
    { version: version },
    {
      $setOnInsert: {
        version: version,
        description: description,
        collection: "facilities",
        appliedAt: new Date(),
      },
    },
    { upsert: true }
  );

  print(`[${version}] done.`);
})();
