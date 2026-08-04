/**
 * 피처 플래그 zod 스키마 및 DTO 타입 (BE 계약 SSOT).
 * BFF Route Handler·훅·컴포넌트가 공통으로 사용한다.
 * BE 계약 근거: 피처 플래그 TDD "API 계약" 섹션.
 */
import { z } from "zod";

import { FEATURE_FLAG_TYPE_VALUES } from "./featureFlagType";
import { FEATURE_FLAG_STATUS_VALUES } from "./featureFlagStatus";
import { FEATURE_FLAG_CHANGE_TYPE_VALUES } from "./featureFlagChangeType";

// ─── 공통 enum ──────────────────────────────────────────────────────────────
//
// 값 목록은 featureFlagType/featureFlagStatus/featureFlagChangeType(라벨 SSOT)이
// 원본이다 — 여기서 다시 나열하면 BE에 값이 추가될 때 한쪽만 갱신된다.

export const FeatureFlagTypeSchema = z.enum(FEATURE_FLAG_TYPE_VALUES);
export type FeatureFlagType = z.infer<typeof FeatureFlagTypeSchema>;

export const FeatureFlagStatusSchema = z.enum(FEATURE_FLAG_STATUS_VALUES);
export type FeatureFlagStatus = z.infer<typeof FeatureFlagStatusSchema>;

export const StrategyTypeSchema = z.enum([
  "GLOBAL_TOGGLE",
  "PERCENTAGE_ROLLOUT",
  "ATTRIBUTE_MATCH",
  "VARIANT_BUCKETING",
]);
export type StrategyType = z.infer<typeof StrategyTypeSchema>;

export const ChangeTypeSchema = z.enum(FEATURE_FLAG_CHANGE_TYPE_VALUES);
export type ChangeType = z.infer<typeof ChangeTypeSchema>;

// ─── 응답 전용 — 계약 밖 값 내성 ─────────────────────────────────────────────
//
// **응답에는 enum을 강제하지 않는다.** 목록·감사 로그는 배열을 통째로 parse하므로 계약 밖
// 값이 한 건이라도 섞이면 `parse`가 throw하고 화면은 "총 0건"이 된다(재캡쳐 검수 후속 결함
// #388·02-파트너포털 결제 상태 전량 파싱 실패와 동일 실패 모드). 알려진 값 집합은 union 앞
// 분기로 문서화하되 모르는 값은 원문 그대로 통과시켜 그 행만 원문으로 남게 하고, 한글 표기는
// `featureFlagTypeLabel`/`featureFlagStatusLabel`/`featureFlagChangeTypeLabel`이 담당한다.
// 구조(필드 존재·타입)는 계속 엄하게 본다 — 내성은 이 값들에만 준다.
const FeatureFlagTypeDisplaySchema = z.union([FeatureFlagTypeSchema, z.string()]);
const FeatureFlagStatusDisplaySchema = z.union([FeatureFlagStatusSchema, z.string()]);
const ChangeTypeDisplaySchema = z.union([ChangeTypeSchema, z.string()]);

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
//
// BE nullable 필드는 `.nullish()`로 받는다 (null·undefined 모두 허용).
// BE가 전역 매퍼를 NON_NULL로 두는 동안에는 null 필드의 키가 응답에서 생략되고,
// 매퍼가 기본 동작(null 포함)으로 복원되면 `null`이 온다. 양쪽 다 견뎌야 한다.

export const FeatureFlagSnapshotSchema = z.object({
  key: z.string(),
  type: FeatureFlagTypeDisplaySchema,
  status: FeatureFlagStatusDisplaySchema,
  description: z.string().nullish(),
  strategy: FeatureFlagStrategySchema,
});
export type FeatureFlagSnapshot = z.infer<typeof FeatureFlagSnapshotSchema>;

export const FeatureFlagResponseSchema = z.object({
  id: z.number(),
  key: z.string(),
  type: FeatureFlagTypeDisplaySchema,
  status: FeatureFlagStatusDisplaySchema,
  description: z.string().nullish(),
  strategy: FeatureFlagStrategySchema,
  createdAt: z.string(),
  updatedAt: z.string(),
});
export type FeatureFlagResponse = z.infer<typeof FeatureFlagResponseSchema>;

export const FeatureFlagAuditLogResponseSchema = z.object({
  changeType: ChangeTypeDisplaySchema,
  actorUserId: z.number(),
  // 변경자 표시 이름(닉네임) — 내부 PK(actorUserId)를 화면에 노출하지 않기 위해 BE가 채워 보낸다.
  actorDisplayName: z.string(),
  // BE `FeatureFlagAuditLogResponse.before`는 `FeatureFlagSnapshot?`이고,
  // 직렬화는 NON_NULL(`McpObjectMapperConfig`)이라 CREATED 로그는 `before` 키 자체가 생략된다.
  // `.nullable()`은 null만 허용해 undefined를 거부하므로 화면 전체가 검증 실패했다 → `.nullish()`.
  before: FeatureFlagSnapshotSchema.nullish(),
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
