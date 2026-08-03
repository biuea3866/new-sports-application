/**
 * 어드민 MCP 토큰 zod 스키마 및 DTO 타입.
 * BFF Route Handler와 Client Component에서 공통으로 사용한다.
 */
import { z } from "zod";

// ─── 공통 enum ────────────────────────────────────────────────────────────────

export const McpTokenStatusSchema = z.enum(["ACTIVE", "SUSPENDED", "REVOKED"]);
export type McpTokenStatus = z.infer<typeof McpTokenStatusSchema>;

// BE McpScope 형식: "{verb}:{domain}" 또는 "{verb}:{domain}:{qualifier}"
export const AVAILABLE_SCOPES = [
  "read:facility",
  "write:facility",
  "read:booking",
  "write:booking",
  "read:product",
  "write:product",
] as const;
export type AvailableScope = (typeof AVAILABLE_SCOPES)[number];

// ─── 발급 입력 검증 ───────────────────────────────────────────────────────────

export const IssueMcpTokenInputSchema = z.object({
  name: z.string().min(1, "토큰 이름을 입력해 주세요."),
  scopes: z.array(z.string().min(1)).min(1, "scope를 1개 이상 선택해 주세요."),
  expiresAt: z.string().datetime().nullable(),
});

export type IssueMcpTokenInput = z.infer<typeof IssueMcpTokenInputSchema>;

// ─── BE 응답 타입 ─────────────────────────────────────────────────────────────

// BE는 `McpObjectMapperConfig`(@Primary)의 JsonInclude.NON_NULL로 직렬화하므로
// nullable 필드는 값이 null일 때 **응답 JSON에서 키가 생략**된다.
// 따라서 nullable 필드 타입은 `string | null | undefined`로 열어 두고,
// 화면은 `lib/admin/datetime`의 포맷터로 둘을 동일하게 "값 없음"으로 처리한다.

export interface IssueMcpTokenResponse {
  tokenId: number;
  name: string;
  plainToken: string;
  status: McpTokenStatus;
  expiresAt?: string | null;
  createdAt: string;
}

export interface McpTokenSummary {
  tokenId: number;
  name: string;
  status: McpTokenStatus;
  expiresAt?: string | null;
  lastUsedAt?: string | null;
  createdAt: string;
}

export interface ListMcpTokensResponse {
  tokens: McpTokenSummary[];
}
