/**
 * E2E-01 회원가입 · 로그인 · 토큰 갱신 · 로그아웃
 * 시나리오 파일: qa/e2e/scenarios/auth-register-login.md
 *
 * 모든 케이스는 BE REST API 를 직접 호출한다 (Playwright request fixture).
 */
import { test, expect, request as playwrightRequest } from "@playwright/test";
import { API_URL, uniqueEmail } from "../test/helpers";

test.describe("E2E-01 auth register · login · refresh · logout", () => {
  test("E2E-01-01 신규 이메일로 가입 시 201 Created + Location 헤더가 응답된다", async () => {
    const api = await playwrightRequest.newContext();
    const email = uniqueEmail("e2e01-01");
    const res = await api.post(`${API_URL}/users/register`, {
      data: { email, password: "Passw0rd!" },
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(201);
    const location = res.headers()["location"];
    expect(location, "Location header present").toMatch(/^\/users\/\d+$/);
    const body = await res.json();
    expect(body.id ?? body.userId, "response contains id").toBeDefined();
    await api.dispose();
  });

  test("E2E-01-02 가입한 계정으로 로그인 시 200 + accessToken·refreshToken 발급", async () => {
    const api = await playwrightRequest.newContext();
    const email = uniqueEmail("e2e01-02");
    const password = "Passw0rd!";
    await api.post(`${API_URL}/users/register`, { data: { email, password } });
    const res = await api.post(`${API_URL}/auth/login`, {
      data: { email, password },
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(200);
    const body = await res.json();
    expect(body.accessToken, "accessToken issued").toBeTruthy();
    expect(body.refreshToken, "refreshToken issued").toBeTruthy();
    await api.dispose();
  });

  test("E2E-01-03 refreshToken 으로 토큰 갱신 시 새 accessToken 이 발급된다", async () => {
    const api = await playwrightRequest.newContext();
    const email = uniqueEmail("e2e01-03");
    const password = "Passw0rd!";
    await api.post(`${API_URL}/users/register`, { data: { email, password } });
    const login = await api.post(`${API_URL}/auth/login`, { data: { email, password } });
    const loginBody = await login.json();

    const refresh = await api.post(`${API_URL}/auth/refresh`, {
      data: { refreshToken: loginBody.refreshToken },
      failOnStatusCode: false,
    });
    expect(refresh.status()).toBe(200);
    const refreshBody = await refresh.json();
    expect(refreshBody.accessToken, "new accessToken returned").toBeTruthy();
    await api.dispose();
  });

  test("E2E-01-04 accessToken 으로 logout 호출 시 204 + 같은 토큰 후속 호출 401", async () => {
    const api = await playwrightRequest.newContext();
    const email = uniqueEmail("e2e01-04");
    const password = "Passw0rd!";
    await api.post(`${API_URL}/users/register`, { data: { email, password } });
    const login = await api.post(`${API_URL}/auth/login`, { data: { email, password } });
    const { accessToken } = await login.json();

    const logout = await api.post(`${API_URL}/auth/logout`, {
      headers: { Authorization: `Bearer ${accessToken}` },
      failOnStatusCode: false,
    });
    expect(logout.status()).toBe(204);

    // 동일 토큰으로 logout 재시도 — 무효화됐으면 401, 무효화 미구현이면 401 이외
    const second = await api.post(`${API_URL}/auth/logout`, {
      headers: { Authorization: `Bearer ${accessToken}` },
      failOnStatusCode: false,
    });
    expect(second.status(), "logged-out token should be invalidated").toBe(401);
    await api.dispose();
  });

  test("E2E-01-R01 이미 가입된 이메일로 재가입 시 409 또는 도메인 예외", async () => {
    const api = await playwrightRequest.newContext();
    const email = uniqueEmail("e2e01-r01");
    const password = "Passw0rd!";
    await api.post(`${API_URL}/users/register`, { data: { email, password } });
    const dup = await api.post(`${API_URL}/users/register`, {
      data: { email, password },
      failOnStatusCode: false,
    });
    expect([409, 400, 422]).toContain(dup.status());
    await api.dispose();
  });

  test("E2E-01-R02 잘못된 비밀번호 로그인 시 401 + 토큰 미발급", async () => {
    const api = await playwrightRequest.newContext();
    const email = uniqueEmail("e2e01-r02");
    await api.post(`${API_URL}/users/register`, { data: { email, password: "Passw0rd!" } });
    const res = await api.post(`${API_URL}/auth/login`, {
      data: { email, password: "WrongPass!" },
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(401);
    const text = await res.text();
    expect(text).not.toContain("accessToken");
    await api.dispose();
  });

  test("E2E-01-R03 만료/무효 refreshToken 갱신 시 401", async () => {
    const api = await playwrightRequest.newContext();
    const res = await api.post(`${API_URL}/auth/refresh`, {
      data: { refreshToken: "invalid-refresh-token-value" },
      failOnStatusCode: false,
    });
    expect([401, 400]).toContain(res.status());
    await api.dispose();
  });

  test("E2E-01-E01 이메일 형식 위반 가입 시 400", async () => {
    const api = await playwrightRequest.newContext();
    const res = await api.post(`${API_URL}/users/register`, {
      data: { email: "not-an-email", password: "Passw0rd!" },
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(400);
    await api.dispose();
  });

  test("E2E-01-E02 비밀번호 길이 미달 가입 시 400", async () => {
    const api = await playwrightRequest.newContext();
    const res = await api.post(`${API_URL}/users/register`, {
      data: { email: uniqueEmail("e2e01-e02"), password: "abc" },
      failOnStatusCode: false,
    });
    expect(res.status()).toBe(400);
    await api.dispose();
  });

  test("E2E-01-E03 Authorization 헤더 없이 logout 호출 시 401", async () => {
    const api = await playwrightRequest.newContext();
    const res = await api.post(`${API_URL}/auth/logout`, {
      failOnStatusCode: false,
    });
    expect([401, 400]).toContain(res.status());
    await api.dispose();
  });
});
