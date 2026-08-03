/**
 * 어드민 상단바 인증 상태 판정 계약 테스트.
 *
 * `app/admin/layout.tsx`가 `isAuthenticated = true` / `operatorName = undefined`를 하드코딩해
 * 로그인 여부와 무관하게 항상 `미인증`이 표시됐다(01~10 전 화면 캡쳐 결함).
 * 실제 세션(`lib/server/auth#getSessionInfo`)으로부터 운영자 표기를 유도한다.
 */
import { describe, it, expect } from "vitest";
import { resolveAdminSession } from "../_components/adminSession";

describe("resolveAdminSession", () => {
  it("세션이 있으면 인증 상태로 판정한다", () => {
    const resolved = resolveAdminSession({
      userId: 7,
      email: "operator@sports.app",
      roles: ["ADMIN"],
    });

    expect(resolved.isAuthenticated).toBe(true);
  });

  it("세션 이메일을 운영자 표기로 사용한다", () => {
    const resolved = resolveAdminSession({
      userId: 7,
      email: "operator@sports.app",
      roles: ["ADMIN"],
    });

    expect(resolved.operatorName).toBe("operator@sports.app");
  });

  it("세션이 없으면 미인증으로 판정하고 운영자 표기를 비운다", () => {
    const resolved = resolveAdminSession(null);

    expect(resolved.isAuthenticated).toBe(false);
    expect(resolved.operatorName).toBeUndefined();
  });

  it("이메일이 비어 있으면 userId 기반 표기로 대체한다", () => {
    const resolved = resolveAdminSession({ userId: 42, email: "", roles: ["ADMIN"] });

    expect(resolved.isAuthenticated).toBe(true);
    expect(resolved.operatorName).toBe("운영자 #42");
  });

  // 어드민 콘솔은 ADMIN 롤 보유자만 진입한다.
  // 레포 확립 패턴(app/portal/users/page.tsx:105 등 5곳)과 동일하게 roles.includes("ADMIN")로 판정한다.
  // BE(SecurityConfig)가 403으로 막지만, 인가 없이 콘솔 셸·토큰 발급 폼·플래그 토글 UI에 도달하는 것 자체를 차단한다.
  it("ADMIN 롤이 없는 세션은 인증되지 않은 것으로 판정한다", () => {
    const resolved = resolveAdminSession({
      userId: 7,
      email: "member@sports.app",
      roles: ["FACILITY_OWNER"],
    });

    expect(resolved.isAuthenticated).toBe(false);
    expect(resolved.operatorName).toBeUndefined();
  });

  it("롤이 비어 있는 세션도 차단한다", () => {
    const resolved = resolveAdminSession({ userId: 7, email: "member@sports.app", roles: [] });

    expect(resolved.isAuthenticated).toBe(false);
  });

  it("여러 롤 중 ADMIN이 포함돼 있으면 통과한다", () => {
    const resolved = resolveAdminSession({
      userId: 7,
      email: "owner@sports.app",
      roles: ["FACILITY_OWNER", "ADMIN"],
    });

    expect(resolved.isAuthenticated).toBe(true);
    expect(resolved.operatorName).toBe("owner@sports.app");
  });
});
