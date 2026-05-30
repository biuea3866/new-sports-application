# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: notification-message.spec.ts >> E2E-08 notification · message >> E2E-08-E04 빈 메시지 내용 POST 시 400
- Location: specs/notification-message.spec.ts:178:7

# Error details

```
Error: expect(received).toContain(expected) // indexOf

Expected value: 500
Received array: [400, 422]
```

# Test source

```ts
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
  178 |   test("E2E-08-E04 빈 메시지 내용 POST 시 400", async () => {
  179 |     const api = await playwrightRequest.newContext();
  180 |     const res = await api.post(`${API_URL}/rooms/1/messages`, {
  181 |       headers: { "X-User-Id": "1", "Content-Type": "application/json" },
  182 |       data: { content: "" },
  183 |       failOnStatusCode: false,
  184 |     });
> 185 |     expect([400, 422]).toContain(res.status());
      |                        ^ Error: expect(received).toContain(expected) // indexOf
  186 |     await api.dispose();
  187 |   });
  188 | });
  189 | 
```