/**
 * ADMIN 전용 회원 목록 API 클라이언트.
 * 반드시 서버 사이드(Server Component, Route Handler)에서만 사용한다.
 */
import "server-only";
import { beClient } from "@/lib/server/be-client";
import { AdminUserPageSchema } from "./schemas";
import { throwIfErrorResponse } from "./error";
import type { AdminUser, Page } from "./types";

export async function listAdminUsers(params: {
  page?: number;
  size?: number;
  emailKeyword?: string;
  roleName?: string;
} = {}): Promise<Page<AdminUser>> {
  const searchParams = new URLSearchParams();
  if (params.page !== undefined) searchParams.set("page", String(params.page));
  if (params.size !== undefined) searchParams.set("size", String(params.size));
  if (params.emailKeyword) searchParams.set("emailKeyword", params.emailKeyword);
  if (params.roleName) searchParams.set("roleName", params.roleName);

  const query = searchParams.toString();
  const path = query ? `/admin/users?${query}` : "/admin/users";

  const response = await beClient(path, { method: "GET" });
  await throwIfErrorResponse(response);
  const data: unknown = await response.json();
  return AdminUserPageSchema.parse(data);
}
