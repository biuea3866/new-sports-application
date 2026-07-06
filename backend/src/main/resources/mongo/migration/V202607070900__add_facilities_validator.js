// V202607070900__add_facilities_validator.js
//
// 목적: facilities 컬렉션에 $jsonSchema validator 신설 (신설 — 기존 validator 0건).
// 근거: /Users/biuea/Desktop/dpdpdndn/프로젝트/스포츠앱/모임·커뮤니티/20260707-모집커뮤니티연동-design-db.md
//   "MongoDB — facilities validator 설계" §임베딩 판단 / §$jsonSchema validator 초안 / §S6
//   - validationLevel: "moderate" — 신규/수정 문서만 검증, 기존 문서 소급 검증 없음(전국 시설 문서 다수 존재).
//   - required는 모든 문서에 항상 존재하는 코어 필드만: code, name, type, location.
//   - owner_user_id: long/int/null 허용(유입 경로 다양성 고려, optional).
//   - operating_hours/holidays는 optional — 소유자가 나중에 등록하는 필드. required로 걸면 파괴적(update 차단)이라 걸지 않음.
//   - 인덱스는 추가하지 않음(operating_hours/holidays 대상 쿼리 없음 — 항상 facility 문서와 함께 로드).
//
// 실행: mongosh <connection-string> V202607070900__add_facilities_validator.js
// 멱등: collMod는 동일 validator로 재실행해도 안전(재적용). 컬렉션 미존재 시에만 createCollection으로 생성.
//   _migrations 기록도 upsert로 재실행 안전.
//
// 롤백(역방향 스크립트 — validator 신설 전 상태로 복구):
//   db.runCommand({ collMod: "facilities", validator: {}, validationLevel: "off" });
//   db.getCollection("_migrations").deleteOne({ version: "V202607070900" });

(function () {
  const version = "V202607070900";
  const description = "add_facilities_validator";

  print(`[${version}] applying: ${description}`);

  // 1. validator 정의 (design-db 초안 그대로)
  const facilitiesJsonSchema = {
    bsonType: "object",
    required: ["code", "name", "type", "location"],
    properties: {
      code: { bsonType: "string" },
      name: { bsonType: "string" },
      type: { bsonType: "string" },
      location: { bsonType: "object" },
      owner_user_id: { bsonType: ["long", "int", "null"] },
      operating_hours: {
        bsonType: "array",
        items: {
          bsonType: "object",
          required: ["day_of_week", "open", "close", "slot_duration_minutes", "capacity"],
          properties: {
            day_of_week: { enum: ["MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"] },
            open: { bsonType: "string" },
            close: { bsonType: "string" },
            breaks: { bsonType: "array" },
            slot_duration_minutes: { bsonType: "int", minimum: 1 },
            capacity: { bsonType: "int", minimum: 1 },
          },
        },
      },
      holidays: { bsonType: "array", items: { bsonType: "string" } },
    },
  };

  // 2. 컬렉션 존재 확인 후 신설 or collMod (멱등)
  const collectionExists = db.getCollectionInfos({ name: "facilities" }).length > 0;

  if (!collectionExists) {
    db.createCollection("facilities", {
      validator: { $jsonSchema: facilitiesJsonSchema },
      validationLevel: "moderate",
    });
    print(`[${version}] facilities collection created with validator.`);
  } else {
    db.runCommand({
      collMod: "facilities",
      validator: { $jsonSchema: facilitiesJsonSchema },
      validationLevel: "moderate",
    });
    print(`[${version}] facilities collection existing — validator applied via collMod.`);
  }

  // 3. 적용 확인용 로그 (기존 인덱스는 건드리지 않음)
  const collectionInfo = db.getCollectionInfos({ name: "facilities" })[0];
  print(`[${version}] facilities validationLevel: ${collectionInfo.options.validationLevel}`);

  // 4. 실행 이력 기록 (컨벤션 — 멱등 upsert)
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
