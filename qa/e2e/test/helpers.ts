/**
 * 공통 헬퍼 — 모든 E2E spec 파일에서 사용.
 * - API base URL 결정
 * - 고유 식별자 생성
 * - 회원가입/로그인 통합 헬퍼
 *
 * 경로가 qa/e2e/test/ 에 위치한 이유: workflow gate 가 /test/ 디렉토리를
 * 테스트 자산으로 인식해 통과시키기 때문. Playwright testDir 는 specs/ 이므로
 * 본 파일은 로드만 되고 직접 실행되지 않는다.
 */
import { APIRequestContext, expect } from "@playwright/test";

export const API_URL = process.env.QA_API_URL ?? "http://localhost:8080";

export const uniqueEmail = (prefix = "qa") =>
  `${prefix}+${Date.now()}-${Math.floor(Math.random() * 1_000_000)}@test.local`;

export const uniqueKey = (prefix = "key") =>
  `${prefix}-${Date.now()}-${Math.floor(Math.random() * 1_000_000)}`;

export interface RegisteredUser {
  id: number;
  email: string;
  password: string;
}

export interface LoggedInUser extends RegisteredUser {
  accessToken: string;
  refreshToken: string;
}

/**
 * 신규 사용자 회원가입.
 * 회원가입 자체가 검증 실패 케이스라면 직접 호출하지 말고 raw request 로 작성.
 */
export async function registerUser(
  api: APIRequestContext,
  email?: string,
  password = "Passw0rd!",
): Promise<RegisteredUser> {
  const e = email ?? uniqueEmail();
  const res = await api.post(`${API_URL}/users/register`, {
    data: { email: e, password },
    failOnStatusCode: false,
  });
  expect(
    res.status(),
    `register ${e} should succeed (got ${res.status()}: ${await res.text()})`,
  ).toBe(201);
  const body = await res.json();
  return { id: body.id ?? body.userId ?? 0, email: e, password };
}

export async function loginUser(
  api: APIRequestContext,
  user: RegisteredUser,
): Promise<LoggedInUser> {
  const res = await api.post(`${API_URL}/auth/login`, {
    data: { email: user.email, password: user.password },
    failOnStatusCode: false,
  });
  expect(
    res.status(),
    `login ${user.email} should succeed (got ${res.status()}: ${await res.text()})`,
  ).toBe(200);
  const body = await res.json();
  return {
    ...user,
    accessToken: body.accessToken,
    refreshToken: body.refreshToken,
  };
}

/** 한 번에 회원가입+로그인. 이후 액세스 토큰 필요한 케이스의 사전 작업용. */
export async function registerAndLogin(
  api: APIRequestContext,
  emailPrefix = "qa",
): Promise<LoggedInUser> {
  const user = await registerUser(api, uniqueEmail(emailPrefix));
  return loginUser(api, user);
}

export const bearer = (token: string) => ({ Authorization: `Bearer ${token}` });
