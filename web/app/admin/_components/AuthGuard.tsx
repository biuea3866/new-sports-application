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
  const isProduction = process.env.NODE_ENV === "production";
  const allowPreview = process.env["NEXT_PUBLIC_ADMIN_PREVIEW_ENABLED"] === "true";

  // production 안전 가드: 미리보기 플래그가 없으면 어드민 라우트 자체를 차단합니다.
  if (isProduction && !allowPreview) {
    redirect("/login?redirect=/admin");
  }

  // 비-prod 미리보기(캡쳐·로컬 확인)는 세션 없이 화면을 열 수 있게 허용합니다.
  // production 에서는 미리보기 플래그와 무관하게 세션이 없으면 차단합니다.
  const canPreviewWithoutSession = allowPreview && !isProduction;
  if (!isAuthenticated && !canPreviewWithoutSession) {
    redirect("/login?redirect=/admin");
  }

  return children;
}
