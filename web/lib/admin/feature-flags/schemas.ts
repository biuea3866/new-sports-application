/**
 * 피처 플래그 zod 스키마 및 DTO 타입 (BE 계약 SSOT).
 * BFF Route Handler·훅·컴포넌트가 공통으로 사용한다.
 * BE 계약 근거: 피처 플래그 TDD "API 계약" 섹션.
 */
import { z } from "zod";

// ─── 공통 enum ──────────────────────────────────────────────────────────────

export const FeatureFlagTypeSchema = z.enum([
  "RELEASE",
  "OPERATIONAL",
  "EXPERIMENT",
  "ENTITLEMENT",
]);
export type FeatureFlagType = z.infer<typeof FeatureFlagTypeSchema>;

export const FeatureFlagStatusSchema = z.enum(["ACTIVE", "ARCHIVED"]);
export type FeatureFlagStatus = z.infer<typeof FeatureFlagStatusSchema>;

export const StrategyTypeSchema = z.enum([
  "GLOBAL_TOGGLE",
  "PERCENTAGE_ROLLOUT",
  "ATTRIBUTE_MATCH",
  "VARIANT_BUCKETING",
]);
export type StrategyType = z.infer<typeof StrategyTypeSchema>;

export const ChangeTypeSchema = z.enum(["CREATED", "UPDATED", "ARCHIVED", "ACTIVATED"]);
export type ChangeType = z.infer<typeof ChangeTypeSchema>;

// ─── strategy (discriminated union) ────────────────────────────────────────

const MAX_VARIANT_COUNT = 4;
const TOTAL_VARIANT_WEIGHT = 100;

export const GlobalToggleStrategySchema = z.object({
  strategyType: z.literal("GLOBAL_TOGGLE"),
  enabled: z.boolean(),
});

export const PercentageRolloutStrategySchema = z.object({
  strategyType: z.literal("PERCENTAGE_ROLLOUT"),
  percentage: z.number().int().min(0).max(100),
});

export const AttributeMatchStrategySchema = z.object({
  strategyType: z.literal("ATTRIBUTE_MATCH"),
  attribute: z.string().min(1),
  value: z.string().min(1),
});

export const FeatureFlagVariantSchema = z.object({
  name: z.string().min(1),
  weight: z.number().int().min(0),
});
export type FeatureFlagVariant = z.infer<typeof FeatureFlagVariantSchema>;

export const VariantBucketingStrategySchema = z.object({
  strategyType: z.literal("VARIANT_BUCKETING"),
  variants: z
    .array(FeatureFlagVariantSchema)
    .min(1)
    .max(MAX_VARIANT_COUNT)
    .refine(
      (variants) => variants.reduce((sum, variant) => sum + variant.weight, 0) === TOTAL_VARIANT_WEIGHT,
      { message: `variants weight 합은 ${TOTAL_VARIANT_WEIGHT}이어야 합니다.` }
    ),
});

export const FeatureFlagStrategySchema = z.discriminatedUnion("strategyType", [
  GlobalToggleStrategySchema,
  PercentageRolloutStrategySchema,
  AttributeMatchStrategySchema,
  VariantBucketingStrategySchema,
]);
export type FeatureFlagStrategy = z.infer<typeof FeatureFlagStrategySchema>;

// ─── 입력 스키마 ─────────────────────────────────────────────────────────────

export const CreateFeatureFlagInputSchema = z.object({
  key: z.string().min(1, "key를 입력해 주세요."),
  type: FeatureFlagTypeSchema,
  description: z.string().min(1, "설명을 입력해 주세요."),
  strategy: FeatureFlagStrategySchema,
});
export type CreateFeatureFlagInput = z.infer<typeof CreateFeatureFlagInputSchema>;

export const UpdateFeatureFlagInputSchema = z.object({
  description: z.string().min(1, "설명을 입력해 주세요."),
  strategy: FeatureFlagStrategySchema,
});
export type UpdateFeatureFlagInput = z.infer<typeof UpdateFeatureFlagInputSchema>;

// ─── 응답 스키마 ─────────────────────────────────────────────────────────────

export const FeatureFlagSnapshotSchema = z.object({
  key: z.string(),
  type: FeatureFlagTypeSchema,
  status: FeatureFlagStatusSchema,
  description: z.string(),
  strategy: FeatureFlagStrategySchema,
});
export type FeatureFlagSnapshot = z.infer<typeof FeatureFlagSnapshotSchema>;

export const FeatureFlagResponseSchema = z.object({
  id: z.number(),
  key: z.string(),
  type: FeatureFlagTypeSchema,
  status: FeatureFlagStatusSchema,
  description: z.string(),
  strategy: FeatureFlagStrategySchema,
  createdAt: z.string(),
  updatedAt: z.string(),
});
export type FeatureFlagResponse = z.infer<typeof FeatureFlagResponseSchema>;

export const FeatureFlagAuditLogResponseSchema = z.object({
  changeType: ChangeTypeSchema,
  actorUserId: z.number(),
  before: FeatureFlagSnapshotSchema.nullable(),
  after: FeatureFlagSnapshotSchema,
  occurredAt: z.string(),
});
export type FeatureFlagAuditLogResponse = z.infer<typeof FeatureFlagAuditLogResponseSchema>;

// ─── 감사 로그 페이지 응답 (wire — total 포함, BE 계약 확정) ────────────────────
// BE 계약: `ListFeatureFlagAuditLogsResponse`
// { content, totalElements, totalPages, pageNumber, pageSize } (레포 `ListMcpAuditLogsResponse` 선례 미러).

export const FeatureFlagAuditLogPageSchema = z.object({
  content: z.array(FeatureFlagAuditLogResponseSchema),
  totalElements: z.number(),
  totalPages: z.number(),
  pageNumber: z.number(),
  pageSize: z.number(),
});
export type FeatureFlagAuditLogPage = z.infer<typeof FeatureFlagAuditLogPageSchema>;

// ─── 감사 로그 페이지 뷰 (canonical — 화면이 소비) ───────────────────────────────
// wire → canonical 변환 함수 구현은 FE-04(api.ts) 담당. 여기서는 화면 계약 타입만 고정한다.

export interface FeatureFlagAuditLogPageView {
  logs: FeatureFlagAuditLogResponse[];
  total: number;
  page: number;
  size: number;
  totalPages: number;
}
