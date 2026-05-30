# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: notification-message.spec.ts >> E2E-08 notification · message >> E2E-08-05 POST /rooms/{roomId}/messages — 201 또는 4xx (room 시드 의존)
- Location: specs/notification-message.spec.ts:70:7

# Error details

```
Error: expect(received).toContain(expected) // indexOf

Expected value: 429
Received array: [201, 400, 403, 404]
```

# Test source

```ts
  1   | /**
  2   |  * E2E-08 알림 · 메시지
  3   |  * 시나리오: qa/e2e/scenarios/notification-message.md
  4   |  *
  5   |  * 시드 (notif-and-room.sql) 미주입 — 알림/방/메시지는 BE 가 권한 검증과 빈 응답을 일관되게
  6   |  * 반환하는지 위주로 검증한다.
  7   |  */
  8   | import { test, expect, request as playwrightRequest } from "@playwright/test";
  9   | import { API_URL } from "../test/helpers";
  10  | 
  11  | test.describe("E2E-08 notification · message", () => {
  12  |   test("E2E-08-01 GET /notifications/me — 200 + Page 응답", async () => {
  13  |     const api = await playwrightRequest.newContext();
  14  |     const res = await api.get(`${API_URL}/notifications/me`, {
  15  |       headers: { "X-User-Id": "1" },
  16  |       failOnStatusCode: false,
  17  |     });
  18  |     expect(res.status()).toBe(200);
  19  |     const body = await res.json();
  20  |     expect(body).toBeTruthy();
  21  |     // 페이지 구조 (content / items / totalElements 등)
  22  |     const items = body.content ?? body.items ?? body.notifications ?? [];
  23  |     expect(Array.isArray(items)).toBe(true);
  24  |     await api.dispose();
  25  |   });
  26  | 
  27  |   test("E2E-08-02 GET /notifications/me?onlyUnread=true — 결과 모두 미읽음", async () => {
  28  |     const api = await playwrightRequest.newContext();
  29  |     const res = await api.get(`${API_URL}/notifications/me?onlyUnread=true`, {
  30  |       headers: { "X-User-Id": "1" },
  31  |       failOnStatusCode: false,
  32  |     });
  33  |     expect(res.status()).toBe(200);
  34  |     const body = await res.json();
  35  |     const items = body.content ?? body.items ?? body.notifications ?? [];
  36  |     for (const n of items) {
  37  |       if (n.read !== undefined) {
  38  |         expect(n.read).toBe(false);
  39  |       } else if (n.isRead !== undefined) {
  40  |         expect(n.isRead).toBe(false);
  41  |       }
  42  |     }
  43  |     await api.dispose();
  44  |   });
  45  | 
  46  |   test("E2E-08-03 GET /notifications/me/unread-count — 200 + unreadCount 숫자", async () => {
  47  |     const api = await playwrightRequest.newContext();
  48  |     const res = await api.get(`${API_URL}/notifications/me/unread-count`, {
  49  |       headers: { "X-User-Id": "1" },
  50  |       failOnStatusCode: false,
  51  |     });
  52  |     expect(res.status()).toBe(200);
  53  |     const body = await res.json();
  54  |     expect(typeof body.unreadCount).toBe("number");
  55  |     expect(body.unreadCount).toBeGreaterThanOrEqual(0);
  56  |     await api.dispose();
  57  |   });
  58  | 
  59  |   test("E2E-08-04 PATCH /notifications/{id}/read — 200 또는 4xx (시드 의존)", async () => {
  60  |     const api = await playwrightRequest.newContext();
  61  |     // 999999 같은 비존재 id — 404 가 정상
  62  |     const res = await api.patch(`${API_URL}/notifications/999999/read`, {
  63  |       headers: { "X-User-Id": "1" },
  64  |       failOnStatusCode: false,
  65  |     });
  66  |     expect([200, 403, 404]).toContain(res.status());
  67  |     await api.dispose();
  68  |   });
  69  | 
  70  |   test("E2E-08-05 POST /rooms/{roomId}/messages — 201 또는 4xx (room 시드 의존)", async () => {
  71  |     const api = await playwrightRequest.newContext();
  72  |     const res = await api.post(`${API_URL}/rooms/1/messages`, {
  73  |       headers: { "X-User-Id": "1", "Content-Type": "application/json" },
  74  |       data: { content: "qa-e2e-test-message" },
  75  |       failOnStatusCode: false,
  76  |     });
> 77  |     expect([201, 400, 403, 404]).toContain(res.status());
      |                                  ^ Error: expect(received).toContain(expected) // indexOf
  78  |     if (res.status() === 201) {
  79  |       const body = await res.json();
  80  |       expect(body).toBeTruthy();
  81  |     }
  82  |     await api.dispose();
  83  |   });
  84  | 
  85  |   test("E2E-08-06 GET /rooms/{roomId}/messages — 200 또는 4xx", async () => {
  86  |     const api = await playwrightRequest.newContext();
  87  |     const res = await api.get(`${API_URL}/rooms/1/messages`, {
  88  |       headers: { "X-User-Id": "1" },
  89  |       failOnStatusCode: false,
  90  |     });
  91  |     expect([200, 403, 404]).toContain(res.status());
  92  |     if (res.status() === 200) {
  93  |       const body = await res.json();
  94  |       const items = body.content ?? body.messages ?? body.items ?? [];
  95  |       expect(Array.isArray(items)).toBe(true);
  96  |       // 시간 역순 정렬 검증
  97  |       if (items.length > 1) {
  98  |         const times = items
  99  |           .map((m: { createdAt?: string; sentAt?: string }) => m.createdAt ?? m.sentAt)
  100 |           .filter(Boolean);
  101 |         const desc = [...times].sort().reverse();
  102 |         expect(times).toEqual(desc);
  103 |       }
  104 |     }
  105 |     await api.dispose();
  106 |   });
  107 | 
  108 |   test("E2E-08-R01 메시지 cursor 페이징 — 응답에 nextCursor 또는 cursor 필드", async () => {
  109 |     const api = await playwrightRequest.newContext();
  110 |     const res = await api.get(`${API_URL}/rooms/1/messages`, {
  111 |       headers: { "X-User-Id": "1" },
  112 |       failOnStatusCode: false,
  113 |     });
  114 |     if (res.status() !== 200) {
  115 |       test.info().annotations.push({
  116 |         type: "skip-reason",
  117 |         description: `room 시드 부재로 cursor 검증 보류 (응답 ${res.status()})`,
  118 |       });
  119 |       test.skip();
  120 |       return;
  121 |     }
  122 |     const body = await res.json();
  123 |     // cursor 관련 필드 키만 검증 — 값은 시드에 따라 가변
  124 |     const hasCursorField =
  125 |       body.nextCursor !== undefined || body.cursor !== undefined || body.hasNext !== undefined;
  126 |     expect(hasCursorField || (body.content ?? body.messages ?? []).length >= 0).toBe(true);
  127 |     await api.dispose();
  128 |   });
  129 | 
  130 |   test("E2E-08-R02 알림 페이징 기본값 — size 미명시 시 기본 20", async () => {
  131 |     const api = await playwrightRequest.newContext();
  132 |     const res = await api.get(`${API_URL}/notifications/me`, {
  133 |       headers: { "X-User-Id": "1" },
  134 |       failOnStatusCode: false,
  135 |     });
  136 |     expect(res.status()).toBe(200);
  137 |     const body = await res.json();
  138 |     const size = body.pageable?.pageSize ?? body.size;
  139 |     if (size !== undefined) {
  140 |       expect(size).toBe(20);
  141 |     }
  142 |     await api.dispose();
  143 |   });
  144 | 
  145 |   test("E2E-08-E01 다른 user 의 알림 id 로 PATCH 시 403/404", async () => {
  146 |     const api = await playwrightRequest.newContext();
  147 |     const res = await api.patch(`${API_URL}/notifications/1/read`, {
  148 |       headers: { "X-User-Id": "999999" },
  149 |       failOnStatusCode: false,
  150 |     });
  151 |     expect([403, 404]).toContain(res.status());
  152 |     await api.dispose();
  153 |   });
  154 | 
  155 |   test("E2E-08-E02 room 미참여자 POST /messages 시 403", async () => {
  156 |     const api = await playwrightRequest.newContext();
  157 |     const res = await api.post(`${API_URL}/rooms/1/messages`, {
  158 |       headers: { "X-User-Id": "999999", "Content-Type": "application/json" },
  159 |       data: { content: "intruder" },
  160 |       failOnStatusCode: false,
  161 |     });
  162 |     expect([403, 404]).toContain(res.status());
  163 |     await api.dispose();
  164 |   });
  165 | 
  166 |   test("E2E-08-E03 알림 0건 user — unread-count 0", async () => {
  167 |     const api = await playwrightRequest.newContext();
  168 |     const res = await api.get(`${API_URL}/notifications/me/unread-count`, {
  169 |       headers: { "X-User-Id": "9999999" },
  170 |       failOnStatusCode: false,
  171 |     });
  172 |     expect(res.status()).toBe(200);
  173 |     const body = await res.json();
  174 |     expect(body.unreadCount).toBe(0);
  175 |     await api.dispose();
  176 |   });
  177 | 
```