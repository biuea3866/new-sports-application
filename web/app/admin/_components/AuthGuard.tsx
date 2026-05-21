import { ReactNode } from "react";
import { redirect } from "next/navigation";

type AuthGuardProps = {
  children: ReactNode;
  isAuthenticated: boolean;
};

/**
 * 어드민 인증 가드 — 미인증 운영자는 로그인 페이지로 리다이렉트.
 *
 * MVP Phase 1: isAuthenticated 판정은 호출자(layout 또는 페이지)가 BE BFF에서
 * 세션 조회 후 prop으로 전달. 통합된 SSR 인증 미들웨어는 후속 작업.
 *
 * FE-01b(MCP 토큰 UI), FE-02(감사 로그), FE-03(docs)는 본 가드를 통해 보호됩니다.
 */
export function AuthGuard({ children, isAuthenticated }: AuthGuardProps): ReactNode {
  if (!isAuthenticated) {
    redirect("/login?redirect=/admin");
  }
  return children;
}
