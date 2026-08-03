import { ReactNode } from "react";
import { getSessionInfo } from "@/lib/server/auth";
import { AdminSidebar } from "./_components/AdminSidebar";
import { AdminTopbar } from "./_components/AdminTopbar";
import { AuthGuard } from "./_components/AuthGuard";
import { resolveAdminSession } from "./_components/adminSession";

type AdminLayoutProps = {
  children: ReactNode;
};

/**
 * 어드민 전용 layout — Sidebar + Topbar + AuthGuard.
 *
 * 인증 상태는 `access_token` 쿠키 세션에서 유도한다(`lib/server/auth#getSessionInfo`).
 * 이전에는 `isAuthenticated = true` / `operatorName = undefined`를 하드코딩해,
 * 로그인 여부와 무관하게 상단바가 항상 `미인증`을 표시했다.
 * 미인증 시 AuthGuard 가 /login 으로 리다이렉트한다.
 */
export default function AdminLayout({ children }: AdminLayoutProps): JSX.Element {
  const { isAuthenticated, operatorName } = resolveAdminSession(getSessionInfo());

  return (
    <AuthGuard isAuthenticated={isAuthenticated}>
      <div className="flex min-h-screen flex-col">
        <AdminTopbar operatorName={operatorName} />
        <div className="flex flex-1">
          <AdminSidebar />
          <main className="flex-1 p-6">{children}</main>
        </div>
      </div>
    </AuthGuard>
  );
}
