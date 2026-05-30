import { redirect } from "next/navigation";
import { getB2BRoles } from "@/lib/server/auth";
import { listAdminUsers } from "@/lib/portal/adminUsers";
import { PortalApiError } from "@/lib/portal/error";
import type { AdminUser, Page } from "@/lib/portal/types";

export const metadata = {
  title: "회원 — 사업자 포털",
};

// ─── 상태 뱃지 ────────────────────────────────────────────────────────────────

const STATUS_LABEL: Record<string, string> = {
  ACTIVE: "활성",
  INACTIVE: "비활성",
  SUSPENDED: "정지",
};

const STATUS_CLASS: Record<string, string> = {
  ACTIVE: "bg-green-100 text-green-800",
  INACTIVE: "bg-gray-100 text-gray-700",
  SUSPENDED: "bg-red-100 text-red-800",
};

interface StatusBadgeProps {
  status: string;
}

function StatusBadge({ status }: StatusBadgeProps) {
  const label = STATUS_LABEL[status] ?? status;
  const className = STATUS_CLASS[status] ?? "bg-gray-100 text-gray-700";
  return (
    <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${className}`}>
      {label}
    </span>
  );
}

// ─── 테이블 행 ────────────────────────────────────────────────────────────────

interface UserRowProps {
  user: AdminUser;
}

function UserRow({ user }: UserRowProps) {
  const joinedDate = new Date(user.joinedAt).toLocaleDateString("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });

  return (
    <tr className="border-b last:border-0">
      <td className="py-3 px-4 text-sm tabular-nums">{user.userId}</td>
      <td className="py-3 px-4 text-sm">{user.email}</td>
      <td className="py-3 px-4">
        <StatusBadge status={user.status} />
      </td>
      <td className="py-3 px-4 text-sm text-muted-foreground">
        {user.roleNames.length > 0 ? user.roleNames.join(", ") : "—"}
      </td>
      <td className="py-3 px-4 text-sm text-muted-foreground tabular-nums">{joinedDate}</td>
    </tr>
  );
}

// ─── 페이지네이션 ─────────────────────────────────────────────────────────────

interface PaginationInfoProps {
  page: number;
  totalPages: number;
  totalElements: number;
  size: number;
}

function PaginationInfo({ page, totalPages, totalElements, size }: PaginationInfoProps) {
  const from = totalElements === 0 ? 0 : page * size + 1;
  const to = Math.min((page + 1) * size, totalElements);

  return (
    <p className="text-sm text-muted-foreground" aria-live="polite">
      전체 {totalElements.toLocaleString("ko-KR")}명 중 {from}–{to}번째 표시
      {totalPages > 1 && ` (${page + 1} / ${totalPages} 페이지)`}
    </p>
  );
}

// ─── 페이지 ───────────────────────────────────────────────────────────────────

interface UsersPageProps {
  searchParams?: { page?: string };
}

export default async function UsersPage({ searchParams }: UsersPageProps) {
  const roles = getB2BRoles();
  if (!roles.includes("ADMIN")) {
    redirect("/portal");
  }

  const currentPage = Math.max(0, Number(searchParams?.page ?? "0") - 1);

  let userPage: Page<AdminUser> | null = null;
  let errorMessage: string | null = null;

  try {
    userPage = await listAdminUsers({ page: currentPage, size: 20 });
  } catch (err) {
    if (err instanceof PortalApiError) {
      errorMessage = err.userMessage;
    } else {
      errorMessage = "회원 목록을 불러오지 못했습니다.";
    }
  }

  return (
    <div className="max-w-5xl mx-auto px-6 py-8 space-y-6">
      <div>
        <h1 className="text-2xl font-bold tracking-tight">회원 목록</h1>
        <p className="text-sm text-muted-foreground mt-1">전체 가입 회원을 조회합니다.</p>
      </div>

      {errorMessage !== null && (
        <div
          role="alert"
          className="rounded-md border border-destructive/50 bg-destructive/10 p-4 text-sm text-destructive"
        >
          {errorMessage}
        </div>
      )}

      {userPage !== null && (
        <>
          {userPage.totalElements === 0 ? (
            <p className="text-sm text-muted-foreground py-8 text-center">
              등록된 회원이 없습니다.
            </p>
          ) : (
            <div className="rounded-md border overflow-hidden">
              <table className="w-full text-left" aria-label="회원 목록">
                <thead className="bg-muted/50">
                  <tr>
                    <th scope="col" className="py-3 px-4 text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                      ID
                    </th>
                    <th scope="col" className="py-3 px-4 text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                      이메일
                    </th>
                    <th scope="col" className="py-3 px-4 text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                      상태
                    </th>
                    <th scope="col" className="py-3 px-4 text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                      역할
                    </th>
                    <th scope="col" className="py-3 px-4 text-xs font-semibold text-muted-foreground uppercase tracking-wider">
                      가입일
                    </th>
                  </tr>
                </thead>
                <tbody>
                  {userPage.content.map((user) => (
                    <UserRow key={user.userId} user={user} />
                  ))}
                </tbody>
              </table>
            </div>
          )}

          {userPage.totalElements > 0 && (
            <PaginationInfo
              page={userPage.page}
              totalPages={userPage.totalPages}
              totalElements={userPage.totalElements}
              size={userPage.size}
            />
          )}
        </>
      )}
    </div>
  );
}
