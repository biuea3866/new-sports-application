import { ReactNode } from "react";
import { AdminSidebar } from "./_components/AdminSidebar";
import { AdminTopbar } from "./_components/AdminTopbar";
import { AuthGuard } from "./_components/AuthGuard";

type AdminLayoutProps = {
  children: ReactNode;
};

/**
 * 어드민 전용 layout — Sidebar + Topbar + AuthGuard.
 *
 * MVP Phase 1: 인증 판정은 placeholder (true). 실제 BFF 세션 조회는 후속 PR에서
 * Server Component fetcher로 통합. 미인증 시 AuthGuard 가 /login 으로 리다이렉트.
 */
export default function AdminLayout({ children }: AdminLayoutProps): JSX.Element {
  // TODO(MCP-FE-01b): BFF 세션 조회로 isAuthenticated · operatorName 결정
  const isAuthenticated = true;
  const operatorName: string | undefined = undefined;

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
