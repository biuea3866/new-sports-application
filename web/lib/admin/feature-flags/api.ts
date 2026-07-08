/**
 * 피처 플래그 BFF fetch 함수.
 * Client Component는 이 함수만 사용하고 BE·raw fetch를 직접 호출하지 않는다(no-direct-fetch).
 * 응답은 FE-01 zod 스키마(schemas.ts)로 `.parse`해 좁힌다(no-loose-assertion).
 * 근거 티켓: `FE-04-hooks-api-client.md`, 근거 설계: `design-fe-web.md` "API 연동 표".
 */
import { z } from "zod";

import {
  FeatureFlagResponseSchema,
  FeatureFlagStatusSchema,
  FeatureFlagAuditLogPageSchema,
  type FeatureFlagResponse,
  type FeatureFlagStatus,
  type FeatureFlagType,
  type CreateFeatureFlagInput,
  type UpdateFeatureFlagInput,
  type FeatureFlagAuditLogPageView,
} from "./schemas";

const BASE_PATH = "/api/admin/feature-flags";

/** BFF 실패 응답 `{message}`을 파싱해 사용자 메시지를 뽑는다. 실패 시 fallback 메시지를 사용한다. */
async function extractErrorMessage(res: Response, fallback: string): Promise<string> {
  const body = (await res.json().catch(() => null)) as { message?: string } | null;
  return body?.message ?? fallback;
}

/** wire 응답이 배열이든 `{content|items,...}` 래핑이든 원소 배열만 뽑아낸다. */
function extractArray(json: unknown): unknown[] {
  if (Array.isArray(json)) return json;
  if (json !== null && typeof json === "object") {
    const record = json as Record<string, unknown>;
    if (Array.isArray(record["content"])) return record["content"];
    if (Array.isArray(record["items"])) return record["items"];
  }
  throw new Error("피처 플래그 목록 응답 형식이 올바르지 않습니다.");
}

export interface FetchFeatureFlagsFilters {
  status?: FeatureFlagStatus;
  type?: FeatureFlagType;
}

/** `GET /api/admin/feature-flags` — status/type 필터로 목록을 조회한다. */
export async function fetchFeatureFlags(
  filters: FetchFeatureFlagsFilters = {}
): Promise<FeatureFlagResponse[]> {
  const query = new URLSearchParams();
  if (filters.status !== undefined) query.set("status", filters.status);
  if (filters.type !== undefined) query.set("type", filters.type);
  const queryString = query.toString();

  const res = await fetch(`${BASE_PATH}${queryString ? `?${queryString}` : ""}`, {
    cache: "no-store",
  });
  if (!res.ok) {
    throw new Error(await extractErrorMessage(res, "피처 플래그 목록을 불러오지 못했습니다."));
  }

  const json = (await res.json()) as unknown;
  return z.array(FeatureFlagResponseSchema).parse(extractArray(json));
}

/** `GET /api/admin/feature-flags/{key}` — 단일 플래그를 조회한다. */
export async function fetchFeatureFlag(key: string): Promise<FeatureFlagResponse> {
  const res = await fetch(`${BASE_PATH}/${encodeURIComponent(key)}`, { cache: "no-store" });
  if (!res.ok) {
    throw new Error(await extractErrorMessage(res, "피처 플래그를 불러오지 못했습니다."));
  }
  return FeatureFlagResponseSchema.parse(await res.json());
}

/** `POST /api/admin/feature-flags` — 신규 플래그를 생성한다. */
export async function createFeatureFlag(
  input: CreateFeatureFlagInput
): Promise<FeatureFlagResponse> {
  const res = await fetch(BASE_PATH, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  if (!res.ok) {
    throw new Error(await extractErrorMessage(res, "피처 플래그 생성에 실패했습니다."));
  }
  return FeatureFlagResponseSchema.parse(await res.json());
}

/** `PUT /api/admin/feature-flags/{key}` — description·strategy를 수정한다. */
export async function updateFeatureFlag(
  key: string,
  input: UpdateFeatureFlagInput
): Promise<FeatureFlagResponse> {
  const res = await fetch(`${BASE_PATH}/${encodeURIComponent(key)}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(input),
  });
  if (!res.ok) {
    throw new Error(await extractErrorMessage(res, "피처 플래그 수정에 실패했습니다."));
  }
  return FeatureFlagResponseSchema.parse(await res.json());
}

const FeatureFlagStatusChangeResponseSchema = z.object({
  key: z.string(),
  status: FeatureFlagStatusSchema,
});
export type FeatureFlagStatusChangeResponse = z.infer<typeof FeatureFlagStatusChangeResponseSchema>;

/** `POST /api/admin/feature-flags/{key}/archive` — 플래그를 아카이브한다. */
export async function archiveFeatureFlag(key: string): Promise<FeatureFlagStatusChangeResponse> {
  const res = await fetch(`${BASE_PATH}/${encodeURIComponent(key)}/archive`, { method: "POST" });
  if (!res.ok) {
    throw new Error(await extractErrorMessage(res, "피처 플래그 아카이브에 실패했습니다."));
  }
  return FeatureFlagStatusChangeResponseSchema.parse(await res.json());
}

/** `POST /api/admin/feature-flags/{key}/activate` — 아카이브된 플래그를 재활성화한다. */
export async function activateFeatureFlag(key: string): Promise<FeatureFlagStatusChangeResponse> {
  const res = await fetch(`${BASE_PATH}/${encodeURIComponent(key)}/activate`, { method: "POST" });
  if (!res.ok) {
    throw new Error(await extractErrorMessage(res, "피처 플래그 재활성화에 실패했습니다."));
  }
  return FeatureFlagStatusChangeResponseSchema.parse(await res.json());
}

/**
 * 감사 로그 wire 응답의 최소 형태.
 * `totalPages`를 optional로 열어 두어, BE가 아직 계산해 주지 않는 경우도 이 함수 단독으로 검증 가능하게 한다.
 */
interface FeatureFlagAuditLogPageWireLike {
  content: FeatureFlagAuditLogPageView["logs"];
  totalElements: number;
  totalPages?: number;
  pageNumber: number;
  pageSize: number;
}

/**
 * wire(`FeatureFlagAuditLogPageSchema`)를 canonical `FeatureFlagAuditLogPageView`로 정규화한다.
 * BE 최종 필드명이 바뀌어도(예: `{items,total}`) 이 함수만 수정하면 훅·화면은 무영향이다.
 */
export function toAuditLogPageView(
  wire: FeatureFlagAuditLogPageWireLike
): FeatureFlagAuditLogPageView {
  const totalPages = wire.totalPages ?? Math.ceil(wire.totalElements / wire.pageSize);
  return {
    logs: wire.content,
    total: wire.totalElements,
    page: wire.pageNumber,
    size: wire.pageSize,
    totalPages,
  };
}

/** `GET /api/admin/feature-flags/{key}/audit-logs?page=&size=` — 플래그별 변경 이력을 페이징 조회한다. */
export async function fetchFlagAuditLogs(
  key: string,
  page = 0,
  size = 20
): Promise<FeatureFlagAuditLogPageView> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  const res = await fetch(
    `${BASE_PATH}/${encodeURIComponent(key)}/audit-logs?${query.toString()}`,
    { cache: "no-store" }
  );
  if (!res.ok) {
    throw new Error(await extractErrorMessage(res, "변경 이력을 불러오지 못했습니다."));
  }
  const wire = FeatureFlagAuditLogPageSchema.parse(await res.json());
  return toAuditLogPageView(wire);
}
