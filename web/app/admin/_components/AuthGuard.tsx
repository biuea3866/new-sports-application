import { ReactNode } from "react";
import { redirect } from "next/navigation";

type AuthGuardProps = {
  children: ReactNode;
  isAuthenticated: boolean;
};

/**
 * 어드민 인증 가드 — 미인증 운영자는 로그인 페이지로 리다이렉트.
 *
 * isAuthenticated 는 `layout.tsx` 가 실제 세션(`lib/server/auth#getSessionInfo`)에서 유도해 전달합니다.
 *
 * FE-01b(MCP 토큰 UI), FE-02(감사 로그), FE-03(docs)는 본 가드를 통해 보호됩니다.
 */
export function AuthGuard({ children, isAuthenticated }: AuthGuardProps): ReactNode {
  // Phase 1 안전 가드: production 환경에서는 어드민 라우트 자체를 차단해 미인증 노출을 막습니다.
  // NEXT_PUBLIC_ADMIN_PREVIEW_ENABLED=true 인 비-prod 환경에서만 이 차단을 통과합니다.
  const allowPreview = process.env["NEXT_PUBLIC_ADMIN_PREVIEW_ENABLED"] === "true";
  if (process.env.NODE_ENV === "production" && !allowPreview) {
    redirect("/login?redirect=/admin");
  }

  // 세션이 없으면 환경과 무관하게 차단합니다.
  // 미리보기 플래그는 위 라우트 차단만 완화할 뿐, 세션 없는 렌더를 허용하지 않습니다 —
  // 세션 없이 화면이 열리면 인증이 깨져도 캡쳐가 정상처럼 보여 결함을 가립니다.
  if (!isAuthenticated) {
    redirect("/login?redirect=/admin");
  }

  return children;
}
