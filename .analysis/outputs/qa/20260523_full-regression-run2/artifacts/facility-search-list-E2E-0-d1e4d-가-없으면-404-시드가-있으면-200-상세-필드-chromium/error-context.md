# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: facility-search-list.spec.ts >> E2E-02 facility search · detail >> E2E-02-04 시설 상세 조회 — 시드가 없으면 404, 시드가 있으면 200 + 상세 필드
- Location: specs/facility-search-list.spec.ts:57:7

# Error details

```
Error: expect(received).toBe(expected) // Object.is equality

Expected: 200
Received: 429
```

# Test source

```ts
  1   | /**
  2   |  * E2E-02 시설 검색 · 상세 조회
  3   |  * 시나리오: qa/e2e/scenarios/facility-search-list.md
  4   |  *
  5   |  * 주의: 시나리오는 SQL fixture(`facilities-multi-gu.sql`, 강남구 5건 등) 를 가정한다.
  6   |  * 현재 환경에는 사전 시드가 없으므로 spec 은 "응답 스키마/상태/필터 동작" 까지만 검증한다.
  7   |  * 개수 단언은 정확값이 아닌 "응답 일관성"(필터 적용 시 다른 구가 섞이지 않음) 으로 완화.
  8   |  */
  9   | import { test, expect, request as playwrightRequest } from "@playwright/test";
  10  | import { API_URL } from "../test/helpers";
  11  | 
  12  | test.describe("E2E-02 facility search · detail", () => {
  13  |   test("E2E-02-01 GET /facilities?page=0&size=50 호출 시 200 + Page 응답 구조", async () => {
  14  |     const api = await playwrightRequest.newContext();
  15  |     const res = await api.get(`${API_URL}/facilities?page=0&size=50`, {
  16  |       failOnStatusCode: false,
  17  |     });
  18  |     expect(res.status()).toBe(200);
  19  |     const body = await res.json();
  20  |     expect(body).toHaveProperty("content");
  21  |     expect(Array.isArray(body.content)).toBe(true);
  22  |     expect(body).toHaveProperty("totalElements");
  23  |     await api.dispose();
  24  |   });
  25  | 
  26  |   test("E2E-02-02 gu=강남구 필터 시 응답에 강남구 외 시설이 포함되지 않는다", async () => {
  27  |     const api = await playwrightRequest.newContext();
  28  |     const res = await api.get(`${API_URL}/facilities?gu=${encodeURIComponent("강남구")}`, {
  29  |       failOnStatusCode: false,
  30  |     });
  31  |     expect(res.status()).toBe(200);
  32  |     const body = await res.json();
  33  |     for (const item of body.content ?? []) {
  34  |       // 시설 응답에 gu 필드가 있으면 검증, 없는 스키마라면 통과
  35  |       if (item.gu !== undefined) {
  36  |         expect(item.gu).toBe("강남구");
  37  |       }
  38  |     }
  39  |     await api.dispose();
  40  |   });
  41  | 
  42  |   test("E2E-02-03 type=풋살장 필터 시 다른 타입이 포함되지 않는다", async () => {
  43  |     const api = await playwrightRequest.newContext();
  44  |     const res = await api.get(`${API_URL}/facilities?type=${encodeURIComponent("풋살장")}`, {
  45  |       failOnStatusCode: false,
  46  |     });
  47  |     expect(res.status()).toBe(200);
  48  |     const body = await res.json();
  49  |     for (const item of body.content ?? []) {
  50  |       if (item.type !== undefined) {
  51  |         expect(item.type).toBe("풋살장");
  52  |       }
  53  |     }
  54  |     await api.dispose();
  55  |   });
  56  | 
  57  |   test("E2E-02-04 시설 상세 조회 — 시드가 없으면 404, 시드가 있으면 200 + 상세 필드", async () => {
  58  |     const api = await playwrightRequest.newContext();
  59  |     // 먼저 목록에서 임의의 id 를 얻어 상세 조회
  60  |     const list = await api.get(`${API_URL}/facilities?page=0&size=1`, { failOnStatusCode: false });
> 61  |     expect(list.status()).toBe(200);
      |                           ^ Error: expect(received).toBe(expected) // Object.is equality
  62  |     const listBody = await list.json();
  63  |     if (!listBody.content || listBody.content.length === 0) {
  64  |       test.info().annotations.push({
  65  |         type: "skip-reason",
  66  |         description: "시설 시드가 비어 있어 상세 조회 건너뜀 (fixture 미주입)",
  67  |       });
  68  |       test.skip();
  69  |       return;
  70  |     }
  71  |     const id = listBody.content[0].id ?? listBody.content[0].facilityId;
  72  |     const detail = await api.get(`${API_URL}/facilities/${id}`, { failOnStatusCode: false });
  73  |     expect(detail.status()).toBe(200);
  74  |     const detailBody = await detail.json();
  75  |     expect(detailBody.id ?? detailBody.facilityId).toBeDefined();
  76  |     await api.dispose();
  77  |   });
  78  | 
  79  |   test("E2E-02-05 시설의 슬롯 목록 조회 시 200 + 배열 반환 (정렬 검증 포함)", async () => {
  80  |     const api = await playwrightRequest.newContext();
  81  |     const list = await api.get(`${API_URL}/facilities?page=0&size=1`, { failOnStatusCode: false });
  82  |     const listBody = await list.json();
  83  |     if (!listBody.content || listBody.content.length === 0) {
  84  |       test.info().annotations.push({
  85  |         type: "skip-reason",
  86  |         description: "시설 시드가 비어 있어 슬롯 조회 건너뜀",
  87  |       });
  88  |       test.skip();
  89  |       return;
  90  |     }
  91  |     const facilityId = listBody.content[0].id ?? listBody.content[0].facilityId;
  92  |     const res = await api.get(`${API_URL}/facilities/${facilityId}/slots`, {
  93  |       failOnStatusCode: false,
  94  |     });
  95  |     expect(res.status()).toBe(200);
  96  |     const body = await res.json();
  97  |     expect(Array.isArray(body)).toBe(true);
  98  |     if (body.length > 1) {
  99  |       const starts = body.map((s: { startsAt?: string }) => s.startsAt).filter(Boolean);
  100 |       const sorted = [...starts].sort();
  101 |       expect(starts).toEqual(sorted);
  102 |     }
  103 |     await api.dispose();
  104 |   });
  105 | 
  106 |   test("E2E-02-R01 GET /facilities/stats/gu-type 호출 시 200 + 구·종목별 카운트 배열", async () => {
  107 |     const api = await playwrightRequest.newContext();
  108 |     const res = await api.get(`${API_URL}/facilities/stats/gu-type`, { failOnStatusCode: false });
  109 |     expect(res.status()).toBe(200);
  110 |     const body = await res.json();
  111 |     expect(Array.isArray(body)).toBe(true);
  112 |     await api.dispose();
  113 |   });
  114 | 
  115 |   test("E2E-02-R02 페이지 size 미명시 시 기본값 50 유지", async () => {
  116 |     const api = await playwrightRequest.newContext();
  117 |     const res = await api.get(`${API_URL}/facilities`, { failOnStatusCode: false });
  118 |     expect(res.status()).toBe(200);
  119 |     const body = await res.json();
  120 |     // pageable.pageSize 또는 size 필드 확인
  121 |     const pageSize = body.pageable?.pageSize ?? body.size;
  122 |     if (pageSize !== undefined) {
  123 |       expect(pageSize).toBe(50);
  124 |     }
  125 |     await api.dispose();
  126 |   });
  127 | 
  128 |   test("E2E-02-E01 존재하지 않는 시설 id 상세 조회 시 404", async () => {
  129 |     const api = await playwrightRequest.newContext();
  130 |     const res = await api.get(`${API_URL}/facilities/nonexistent-id-xxx`, {
  131 |       failOnStatusCode: false,
  132 |     });
  133 |     expect([404, 400]).toContain(res.status());
  134 |     await api.dispose();
  135 |   });
  136 | 
  137 |   test("E2E-02-E02 존재하지 않는 gu 로 조회 시 200 + 빈 페이지", async () => {
  138 |     const api = await playwrightRequest.newContext();
  139 |     const res = await api.get(
  140 |       `${API_URL}/facilities?gu=${encodeURIComponent("존재하지않는구")}`,
  141 |       { failOnStatusCode: false },
  142 |     );
  143 |     expect(res.status()).toBe(200);
  144 |     const body = await res.json();
  145 |     expect(body.content).toEqual([]);
  146 |     expect(body.totalElements).toBe(0);
  147 |     await api.dispose();
  148 |   });
  149 | 
  150 |   test("E2E-02-E03 슬롯이 없는 시설의 /slots 조회 시 200 + 빈 배열", async () => {
  151 |     const api = await playwrightRequest.newContext();
  152 |     // 존재하지 않는 facility id 라도 200 + [] 인지 또는 404 인지 확인
  153 |     const res = await api.get(`${API_URL}/facilities/no-such-slot-facility/slots`, {
  154 |       failOnStatusCode: false,
  155 |     });
  156 |     // 시설이 없으면 404 도 정당. 빈 배열도 정당.
  157 |     expect([200, 404]).toContain(res.status());
  158 |     if (res.status() === 200) {
  159 |       const body = await res.json();
  160 |       expect(Array.isArray(body)).toBe(true);
  161 |     }
```