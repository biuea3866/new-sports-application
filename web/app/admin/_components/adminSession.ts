/**
 * 어드민 상단바 인증 상태 판정.
 *
 * `layout.tsx`가 `isAuthenticated = true` / `operatorName = undefined`를 하드코딩해
 * 실제 로그인 여부와 무관하게 항상 `미인증`이 표시됐다(01~10 전 화면 캡쳐 결함).
 * 세션 조회(`lib/server/auth#getSessionInfo`)는 서버 전용이라, 순수 판정만 여기로 분리해 테스트한다.
 */
import type { SessionInfo } from "@/lib/server/auth";

export interface AdminSessionView {
  isAuthenticated: boolean;
  operatorName: string | undefined;
}

/** 어드민 콘솔 진입에 필요한 롤. 레포 확립 패턴(`app/portal/users/page.tsx` 등)과 동일하게 판정한다. */
const REQUIRED_ROLE = "ADMIN";

/**
 * 세션 정보로부터 상단바가 소비할 인증 상태·운영자 표기를 유도한다.
 *
 * ADMIN 롤이 없으면 미인증으로 취급해 AuthGuard가 /login 으로 보내게 한다.
 * BE(`SecurityConfig`)가 `hasRole("ADMIN")`으로 403을 내지만, 인가 없는 사용자가
 * 콘솔 셸·MCP 토큰 발급 폼·플래그 토글 UI에 도달하는 것 자체를 막는다.
 */
export function resolveAdminSession(session: SessionInfo | null): AdminSessionView {
  if (session === null || !session.roles.includes(REQUIRED_ROLE)) {
    return { isAuthenticated: false, operatorName: undefined };
  }

  const operatorName = session.email !== "" ? session.email : `운영자 #${session.userId}`;
  return { isAuthenticated: true, operatorName };
}
