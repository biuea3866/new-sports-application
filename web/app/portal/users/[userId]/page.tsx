import { redirect, notFound } from "next/navigation";
import Link from "next/link";
import { getSessionInfo } from "@/lib/server/auth";
import { getAdminUser } from "@/lib/portal/adminUsers";
import { PortalApiError } from "@/lib/portal/error";
import UserRoleClient from "./UserRoleClient";
import type { AdminUser } from "@/lib/portal/types";

export const metadata = {
  title: "회원 상세 — 사업자 포털",
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
    <span
      className={`inline-flex items-center rounded-full px-2 py-0.5 text-xs font-medium ${className}`}
    >
      {label}
    </span>
  );
}

// ─── 기본 정보 섹션 ────────────────────────────────────────────────────────────

interface UserInfoSectionProps {
  user: AdminUser;
}

function UserInfoSection({ user }: UserInfoSectionProps) {
  const joinedDate = new Date(user.joinedAt).toLocaleDateString("ko-KR", {
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  });

  return (
    <section aria-labelledby="user-info-heading">
      <h2 id="user-info-heading" className="text-lg font-semibold mb-3">
        기본 정보
      </h2>
      <dl className="grid grid-cols-2 gap-x-8 gap-y-4 rounded-lg border border-border p-4">
        <div>
          <dt className="text-xs text-muted-foreground">ID</dt>
          <dd className="text-sm font-medium tabular-nums mt-1">{user.userId}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">이메일</dt>
          <dd className="text-sm font-medium mt-1">{user.email}</dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">상태</dt>
          <dd className="mt-1">
            <StatusBadge status={user.status} />
          </dd>
        </div>
        <div>
          <dt className="text-xs text-muted-foreground">가입일</dt>
          <dd className="text-sm font-medium tabular-nums mt-1">{joinedDate}</dd>
        </div>
      </dl>
    </section>
  );
}

// ─── 페이지 ───────────────────────────────────────────────────────────────────

interface UserDetailPageProps {
  params: { userId: string };
}

export default async function UserDetailPage({ params }: UserDetailPageProps) {
  const session = getSessionInfo();
  if (!session?.roles.includes("ADMIN")) {
    redirect("/portal");
  }

  const userId = Number(params.userId);
  if (!Number.isFinite(userId) || userId <= 0) {
    notFound();
  }

  let user: AdminUser | null = null;
  let errorMessage: string | null = null;

  try {
    user = await getAdminUser(userId);
  } catch (err) {
    if (err instanceof PortalApiError && err.status === 404) {
      notFound();
    }
    if (err instanceof PortalApiError) {
      errorMessage = err.userMessage;
    } else {
      errorMessage = "회원 정보를 불러오지 못했습니다.";
    }
  }

  return (
    <div className="max-w-3xl mx-auto px-6 py-8 space-y-8">
      <div className="flex items-center gap-3">
        <Link
          href="/portal/users"
          className="text-sm text-muted-foreground hover:text-foreground transition-colors"
          aria-label="회원 목록으로 돌아가기"
        >
          ← 목록
        </Link>
        <h1 className="text-2xl font-bold tracking-tight">회원 상세</h1>
      </div>

      {errorMessage !== null && (
        <div
          role="alert"
          className="rounded-md border border-destructive/50 bg-destructive/10 p-4 text-sm text-destructive"
        >
          {errorMessage}
        </div>
      )}

      {user !== null && (
        <>
          <UserInfoSection user={user} />
          <UserRoleClient userId={user.userId} initialRoleNames={user.roleNames} />
        </>
      )}
    </div>
  );
}
