/**
 * 피처 플래그 zod 스키마 파싱 검증 (BE 계약 SSOT).
 */
import { describe, it, expect } from "vitest";
import {
  FeatureFlagStrategySchema,
  FeatureFlagResponseSchema,
  FeatureFlagAuditLogPageSchema,
} from "../schemas";

const baseFlag = {
  id: 1,
  key: "demo.feature.hello",
  type: "RELEASE",
  status: "ACTIVE",
  description: "데모 플래그",
  createdAt: "2026-07-01T00:00:00.000Z",
  updatedAt: "2026-07-01T00:00:00.000Z",
};

const auditLog = {
  changeType: "CREATED",
  actorUserId: 1,
  before: null,
  after: { key: "demo.feature.hello", type: "RELEASE", status: "ACTIVE", description: "d", strategy: { strategyType: "GLOBAL_TOGGLE", enabled: true } },
  occurredAt: "2026-07-01T00:00:00.000Z",
};

describe("FeatureFlagStrategySchema", () => {
  it("GLOBAL_TOGGLE strategy를 파싱한다", () => {
    const result = FeatureFlagStrategySchema.safeParse({ strategyType: "GLOBAL_TOGGLE", enabled: true });
    expect(result.success).toBe(true);
  });

  it("PERCENTAGE_ROLLOUT strategy를 파싱한다", () => {
    const result = FeatureFlagStrategySchema.safeParse({ strategyType: "PERCENTAGE_ROLLOUT", percentage: 50 });
    expect(result.success).toBe(true);
  });

  it("ATTRIBUTE_MATCH strategy를 파싱한다", () => {
    const result = FeatureFlagStrategySchema.safeParse({
      strategyType: "ATTRIBUTE_MATCH",
      attribute: "plan",
      value: "PREMIUM",
    });
    expect(result.success).toBe(true);
  });

  it("VARIANT_BUCKETING strategy를 파싱한다", () => {
    const result = FeatureFlagStrategySchema.safeParse({
      strategyType: "VARIANT_BUCKETING",
      variants: [
        { name: "A", weight: 50 },
        { name: "B", weight: 50 },
      ],
    });
    expect(result.success).toBe(true);
  });

  it("percentage가 101이면 PERCENTAGE_ROLLOUT 파싱이 실패한다", () => {
    const result = FeatureFlagStrategySchema.safeParse({ strategyType: "PERCENTAGE_ROLLOUT", percentage: 101 });
    expect(result.success).toBe(false);
  });

  it("percentage가 소수이면 PERCENTAGE_ROLLOUT 파싱이 실패한다", () => {
    const result = FeatureFlagStrategySchema.safeParse({ strategyType: "PERCENTAGE_ROLLOUT", percentage: 50.5 });
    expect(result.success).toBe(false);
  });

  it("weight가 음수이면 VARIANT_BUCKETING 파싱이 실패한다", () => {
    const result = FeatureFlagStrategySchema.safeParse({
      strategyType: "VARIANT_BUCKETING",
      variants: [
        { name: "A", weight: -10 },
        { name: "B", weight: 110 },
      ],
    });
    expect(result.success).toBe(false);
  });

  it("variants weight 합이 90이면 VARIANT_BUCKETING 파싱이 실패한다", () => {
    const result = FeatureFlagStrategySchema.safeParse({
      strategyType: "VARIANT_BUCKETING",
      variants: [
        { name: "A", weight: 40 },
        { name: "B", weight: 50 },
      ],
    });
    expect(result.success).toBe(false);
  });

  it("variants가 5개면 파싱이 실패한다 (최대 4)", () => {
    const result = FeatureFlagStrategySchema.safeParse({
      strategyType: "VARIANT_BUCKETING",
      variants: [
        { name: "A", weight: 20 },
        { name: "B", weight: 20 },
        { name: "C", weight: 20 },
        { name: "D", weight: 20 },
        { name: "E", weight: 20 },
      ],
    });
    expect(result.success).toBe(false);
  });

  it("strategyType이 계약 외 값이면 discriminatedUnion 파싱이 실패한다", () => {
    const result = FeatureFlagStrategySchema.safeParse({ strategyType: "UNKNOWN_TYPE", enabled: true });
    expect(result.success).toBe(false);
  });
});

describe("FeatureFlagResponseSchema", () => {
  it("유효한 FeatureFlagResponse를 파싱한다", () => {
    const data = { ...baseFlag, strategy: { strategyType: "GLOBAL_TOGGLE", enabled: true } };
    expect(FeatureFlagResponseSchema.safeParse(data).success).toBe(true);
  });

  it("status가 계약 외 값이면 파싱이 실패한다", () => {
    const data = {
      ...baseFlag,
      status: "DELETED",
      strategy: { strategyType: "GLOBAL_TOGGLE", enabled: true },
    };
    expect(FeatureFlagResponseSchema.safeParse(data).success).toBe(false);
  });
});

describe("FeatureFlagAuditLogPageSchema", () => {
  it("total(totalElements) 포함 감사 페이지 응답이 파싱을 통과하고 값이 보존된다", () => {
    const data = {
      content: [auditLog],
      totalElements: 1,
      totalPages: 1,
      pageNumber: 0,
      pageSize: 20,
    };
    const result = FeatureFlagAuditLogPageSchema.safeParse(data);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.totalElements).toBe(1);
    }
  });

  it("totalElements 필드가 없는 응답은 파싱이 실패한다", () => {
    const data = {
      content: [auditLog],
      totalPages: 1,
      pageNumber: 0,
      pageSize: 20,
    };
    expect(FeatureFlagAuditLogPageSchema.safeParse(data).success).toBe(false);
  });
});
