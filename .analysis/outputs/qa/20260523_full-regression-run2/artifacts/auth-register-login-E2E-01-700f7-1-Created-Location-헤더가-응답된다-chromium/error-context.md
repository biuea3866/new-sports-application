# Instructions

- Following Playwright test failed.
- Explain why, be concise, respect Playwright best practices.
- Provide a snippet of code with the fix, if possible.

# Test info

- Name: auth-register-login.spec.ts >> E2E-01 auth register · login · refresh · logout >> E2E-01-01 신규 이메일로 가입 시 201 Created + Location 헤더가 응답된다
- Location: specs/auth-register-login.spec.ts:11:7

# Error details

```
Error: expect(received).toBe(expected) // Object.is equality

Expected: 201
Received: 401
```

# Test source

```ts
  1   | /**
  2   |  * E2E-01 회원가입 · 로그인 · 토큰 갱신 · 로그아웃
  3   |  * 시나리오 파일: qa/e2e/scenarios/auth-register-login.md
  4   |  *
  5   |  * 모든 케이스는 BE REST API 를 직접 호출한다 (Playwright request fixture).
  6   |  */
  7   | import { test, expect, request as playwrightRequest } from "@playwright/test";
  8   | import { API_URL, uniqueEmail } from "../test/helpers";
  9   | 
  10  | test.describe("E2E-01 auth register · login · refresh · logout", () => {
  11  |   test("E2E-01-01 신규 이메일로 가입 시 201 Created + Location 헤더가 응답된다", async () => {
  12  |     const api = await playwrightRequest.newContext();
  13  |     const email = uniqueEmail("e2e01-01");
  14  |     const res = await api.post(`${API_URL}/users/register`, {
  15  |       data: { email, password: "Passw0rd!" },
  16  |       failOnStatusCode: false,
  17  |     });
> 18  |     expect(res.status()).toBe(201);
      |                          ^ Error: expect(received).toBe(expected) // Object.is equality
  19  |     const location = res.headers()["location"];
  20  |     expect(location, "Location header present").toMatch(/^\/users\/\d+$/);
  21  |     const body = await res.json();
  22  |     expect(body.id ?? body.userId, "response contains id").toBeDefined();
  23  |     await api.dispose();
  24  |   });
  25  | 
  26  |   test("E2E-01-02 가입한 계정으로 로그인 시 200 + accessToken·refreshToken 발급", async () => {
  27  |     const api = await playwrightRequest.newContext();
  28  |     const email = uniqueEmail("e2e01-02");
  29  |     const password = "Passw0rd!";
  30  |     await api.post(`${API_URL}/users/register`, { data: { email, password } });
  31  |     const res = await api.post(`${API_URL}/auth/login`, {
  32  |       data: { email, password },
  33  |       failOnStatusCode: false,
  34  |     });
  35  |     expect(res.status()).toBe(200);
  36  |     const body = await res.json();
  37  |     expect(body.accessToken, "accessToken issued").toBeTruthy();
  38  |     expect(body.refreshToken, "refreshToken issued").toBeTruthy();
  39  |     await api.dispose();
  40  |   });
  41  | 
  42  |   test("E2E-01-03 refreshToken 으로 토큰 갱신 시 새 accessToken 이 발급된다", async () => {
  43  |     const api = await playwrightRequest.newContext();
  44  |     const email = uniqueEmail("e2e01-03");
  45  |     const password = "Passw0rd!";
  46  |     await api.post(`${API_URL}/users/register`, { data: { email, password } });
  47  |     const login = await api.post(`${API_URL}/auth/login`, { data: { email, password } });
  48  |     const loginBody = await login.json();
  49  | 
  50  |     const refresh = await api.post(`${API_URL}/auth/refresh`, {
  51  |       data: { refreshToken: loginBody.refreshToken },
  52  |       failOnStatusCode: false,
  53  |     });
  54  |     expect(refresh.status()).toBe(200);
  55  |     const refreshBody = await refresh.json();
  56  |     expect(refreshBody.accessToken, "new accessToken returned").toBeTruthy();
  57  |     await api.dispose();
  58  |   });
  59  | 
  60  |   test("E2E-01-04 accessToken 으로 logout 호출 시 204 + 같은 토큰 후속 호출 401", async () => {
  61  |     const api = await playwrightRequest.newContext();
  62  |     const email = uniqueEmail("e2e01-04");
  63  |     const password = "Passw0rd!";
  64  |     await api.post(`${API_URL}/users/register`, { data: { email, password } });
  65  |     const login = await api.post(`${API_URL}/auth/login`, { data: { email, password } });
  66  |     const { accessToken } = await login.json();
  67  | 
  68  |     const logout = await api.post(`${API_URL}/auth/logout`, {
  69  |       headers: { Authorization: `Bearer ${accessToken}` },
  70  |       failOnStatusCode: false,
  71  |     });
  72  |     expect(logout.status()).toBe(204);
  73  | 
  74  |     // 동일 토큰으로 logout 재시도 — 무효화됐으면 401, 무효화 미구현이면 401 이외
  75  |     const second = await api.post(`${API_URL}/auth/logout`, {
  76  |       headers: { Authorization: `Bearer ${accessToken}` },
  77  |       failOnStatusCode: false,
  78  |     });
  79  |     expect(second.status(), "logged-out token should be invalidated").toBe(401);
  80  |     await api.dispose();
  81  |   });
  82  | 
  83  |   test("E2E-01-R01 이미 가입된 이메일로 재가입 시 409 또는 도메인 예외", async () => {
  84  |     const api = await playwrightRequest.newContext();
  85  |     const email = uniqueEmail("e2e01-r01");
  86  |     const password = "Passw0rd!";
  87  |     await api.post(`${API_URL}/users/register`, { data: { email, password } });
  88  |     const dup = await api.post(`${API_URL}/users/register`, {
  89  |       data: { email, password },
  90  |       failOnStatusCode: false,
  91  |     });
  92  |     expect([409, 400, 422]).toContain(dup.status());
  93  |     await api.dispose();
  94  |   });
  95  | 
  96  |   test("E2E-01-R02 잘못된 비밀번호 로그인 시 401 + 토큰 미발급", async () => {
  97  |     const api = await playwrightRequest.newContext();
  98  |     const email = uniqueEmail("e2e01-r02");
  99  |     await api.post(`${API_URL}/users/register`, { data: { email, password: "Passw0rd!" } });
  100 |     const res = await api.post(`${API_URL}/auth/login`, {
  101 |       data: { email, password: "WrongPass!" },
  102 |       failOnStatusCode: false,
  103 |     });
  104 |     expect(res.status()).toBe(401);
  105 |     const text = await res.text();
  106 |     expect(text).not.toContain("accessToken");
  107 |     await api.dispose();
  108 |   });
  109 | 
  110 |   test("E2E-01-R03 만료/무효 refreshToken 갱신 시 401", async () => {
  111 |     const api = await playwrightRequest.newContext();
  112 |     const res = await api.post(`${API_URL}/auth/refresh`, {
  113 |       data: { refreshToken: "invalid-refresh-token-value" },
  114 |       failOnStatusCode: false,
  115 |     });
  116 |     expect([401, 400]).toContain(res.status());
  117 |     await api.dispose();
  118 |   });
```