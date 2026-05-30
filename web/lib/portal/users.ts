/**
 * 관리자 유저 BFF API 클라이언트.
 * Client Component에서는 /api/portal/users BFF 엔드포인트만 호출한다.
 */
import type { AdminUser, ListUsersParams, Page } from "./types";

/** 유저 목록 조회 (페이징/검색/role 필터) */
export async function fetchAdminUsers(
  params: ListUsersParams = {}
): Promise<Page<AdminUser>> {
  const query = new URLSearchParams();
  if (params.emailKeyword !== undefined) query.set("emailKeyword", params.emailKeyword);
  if (params.roleName !== undefined) query.set("roleName", params.roleName);
  if (params.page !== undefined) query.set("page", String(params.page));
  if (params.size !== undefined) query.set("size", String(params.size));

  const qs = query.toString();
  const url = qs ? `/api/portal/users?${qs}` : "/api/portal/users";

  const res = await fetch(url, { cache: "no-store" });
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message ?? `유저 목록 조회 실패: ${res.status}`);
  }
  return res.json() as Promise<Page<AdminUser>>;
}

/** 역할 부여 */
export async function assignUserRole(userId: number, roleName: string): Promise<void> {
  const res = await fetch(`/api/portal/users/${userId}/roles/${roleName}`, {
    method: "POST",
  });
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message ?? `역할 부여 실패: ${res.status}`);
  }
}

/** 역할 회수 */
export async function revokeUserRole(userId: number, roleName: string): Promise<void> {
  const res = await fetch(`/api/portal/users/${userId}/roles/${roleName}`, {
    method: "DELETE",
  });
  if (!res.ok) {
    const body = (await res.json().catch(() => null)) as { message?: string } | null;
    throw new Error(body?.message ?? `역할 회수 실패: ${res.status}`);
  }
}
