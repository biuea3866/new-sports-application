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

/** 세션 정보로부터 상단바가 소비할 인증 상태·운영자 표기를 유도한다. */
export function resolveAdminSession(session: SessionInfo | null): AdminSessionView {
  if (session === null) {
    return { isAuthenticated: false, operatorName: undefined };
  }

  const operatorName = session.email !== "" ? session.email : `운영자 #${session.userId}`;
  return { isAuthenticated: true, operatorName };
}
