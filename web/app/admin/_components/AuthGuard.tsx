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
  // Phase 1 안전 가드: BFF 세션 통합(FE-01b) 전까지 production 환경에서는
  // 어드민 라우트 자체를 차단해 미인증 노출을 막습니다.
  // NEXT_PUBLIC_ADMIN_PREVIEW_ENABLED=true 인 비-prod 환경에서만 placeholder 통과.
  const allowPreview = process.env["NEXT_PUBLIC_ADMIN_PREVIEW_ENABLED"] === "true";
  if (process.env.NODE_ENV === "production" && !allowPreview) {
    redirect("/login?redirect=/admin");
  }
  if (!isAuthenticated) {
    redirect("/login?redirect=/admin");
  }
  return children;
}
