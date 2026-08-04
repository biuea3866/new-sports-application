/**
 * 피처 플래그 zod 스키마 파싱 검증 (BE 계약 SSOT).
 */
import { describe, it, expect } from "vitest";
import { z } from "zod";
import {
  FeatureFlagStrategySchema,
  FeatureFlagResponseSchema,
  FeatureFlagSnapshotSchema,
  FeatureFlagAuditLogPageSchema,
  FeatureFlagAuditLogResponseSchema,
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
  actorDisplayName: "김철수",
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

  // 목록은 통째로 파싱된다 — status/type 계약 밖 값 하나로 배열 전체가 실패하면 화면은
  // "총 0건" + Zod 에러 원문 노출이 된다(재캡쳐 검수 후속 결함 #388과 동일 실패 모드).
  // enum을 강제하지 않고 원문을 통과시켜 그 행만 원문 라벨로 남긴다 — 구조(필드 존재)는 계속
  // 엄하게 본다, 내성은 이 두 값에만 준다.
  it("status가 계약 외 값이어도 파싱에 성공하고 원문이 보존된다", () => {
    const data = {
      ...baseFlag,
      status: "DELETED",
      strategy: { strategyType: "GLOBAL_TOGGLE", enabled: true },
    };
    const result = FeatureFlagResponseSchema.safeParse(data);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.status).toBe("DELETED");
    }
  });

  it("type이 계약 외 값이어도 파싱에 성공하고 원문이 보존된다", () => {
    const data = {
      ...baseFlag,
      type: "LEGACY_TYPE",
      strategy: { strategyType: "GLOBAL_TOGGLE", enabled: true },
    };
    const result = FeatureFlagResponseSchema.safeParse(data);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.type).toBe("LEGACY_TYPE");
    }
  });

  it("status가 숫자면(구조 파손) 파싱이 실패한다", () => {
    const data = {
      ...baseFlag,
      status: 42,
      strategy: { strategyType: "GLOBAL_TOGGLE", enabled: true },
    };
    expect(FeatureFlagResponseSchema.safeParse(data).success).toBe(false);
  });

  it("description이 null(BE가 설명 없이 반환)이어도 파싱에 성공한다", () => {
    const data = {
      ...baseFlag,
      description: null,
      strategy: { strategyType: "GLOBAL_TOGGLE", enabled: true },
    };
    const result = FeatureFlagResponseSchema.safeParse(data);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.description).toBeNull();
    }
  });

  // BE `FeatureFlagResponse.description: String?` + NON_NULL 직렬화 조합이면
  // 설명 없는 플래그는 description 키가 생략돼 도착한다.
  // 현재 DB엔 NULL description이 0건이라 아직 드러나지 않았을 뿐, 하나만 생겨도 목록·상세가 파손된다.
  it("description 키가 생략된 응답도 파싱에 성공한다", () => {
    const data = {
      id: baseFlag.id,
      key: baseFlag.key,
      type: baseFlag.type,
      status: baseFlag.status,
      createdAt: baseFlag.createdAt,
      updatedAt: baseFlag.updatedAt,
      strategy: { strategyType: "GLOBAL_TOGGLE", enabled: true },
    };

    expect(FeatureFlagResponseSchema.safeParse(data).success).toBe(true);
  });

  // 목록 화면은 배열을 통째로 parse한다 — 계약 밖 값이 섞인 한 행 때문에 나머지 정상 행까지
  // 함께 사라지면 안 된다(02-피처플래그-목록 재발 방지 가드).
  it("계약 밖 status·type이 섞인 배열도 파싱에 성공하고 나머지 행이 보존된다", () => {
    const known = { ...baseFlag, strategy: { strategyType: "GLOBAL_TOGGLE", enabled: true } };
    const unknownStatus = {
      ...baseFlag,
      key: "demo.feature.unknown-status",
      status: "DELETED",
      strategy: { strategyType: "GLOBAL_TOGGLE", enabled: true },
    };
    const unknownType = {
      ...baseFlag,
      key: "demo.feature.unknown-type",
      type: "LEGACY_TYPE",
      strategy: { strategyType: "GLOBAL_TOGGLE", enabled: true },
    };

    const result = z.array(FeatureFlagResponseSchema).safeParse([known, unknownStatus, unknownType]);

    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data).toHaveLength(3);
      expect(result.data[1]?.status).toBe("DELETED");
      expect(result.data[2]?.type).toBe("LEGACY_TYPE");
    }
  });
});

describe("FeatureFlagSnapshotSchema", () => {
  it("description이 null인 감사 로그 스냅샷도 파싱에 성공한다", () => {
    const data = {
      key: "demo.feature.hello",
      type: "RELEASE",
      status: "ACTIVE",
      description: null,
      strategy: { strategyType: "GLOBAL_TOGGLE", enabled: true },
    };
    const result = FeatureFlagSnapshotSchema.safeParse(data);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.description).toBeNull();
    }
  });

  it("description 키가 생략된 스냅샷도 파싱에 성공한다", () => {
    const data = {
      key: "demo.feature.hello",
      type: "RELEASE",
      status: "ACTIVE",
      strategy: { strategyType: "GLOBAL_TOGGLE", enabled: true },
    };

    expect(FeatureFlagSnapshotSchema.safeParse(data).success).toBe(true);
  });
});

describe("FeatureFlagAuditLogResponseSchema", () => {
  it("actorDisplayName을 포함해 파싱한다", () => {
    const result = FeatureFlagAuditLogResponseSchema.safeParse(auditLog);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.actorDisplayName).toBe("김철수");
    }
  });

  it("actorDisplayName이 없으면 파싱이 실패한다", () => {
    const withoutActorDisplayName: Record<string, unknown> = { ...auditLog };
    delete withoutActorDisplayName.actorDisplayName;
    expect(FeatureFlagAuditLogResponseSchema.safeParse(withoutActorDisplayName).success).toBe(false);
  });

  // 05-피처플래그-감사로그 결함 재발 방지 — 변경 유형이 계약 밖 값이어도 그 행만 원문으로 남고
  // 로그 전체 파싱이 죽지 않아야 한다.
  it("changeType이 계약 외 값이어도 파싱에 성공하고 원문이 보존된다", () => {
    const data = { ...auditLog, changeType: "ROLLED_BACK" };
    const result = FeatureFlagAuditLogResponseSchema.safeParse(data);
    expect(result.success).toBe(true);
    if (result.success) {
      expect(result.data.changeType).toBe("ROLLED_BACK");
    }
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

  // ─── 실제 BE 계약 회귀 ─────────────────────────────────────────────────────
  // BE `McpObjectMapperConfig`(@Primary)는 JsonInclude.NON_NULL 이므로
  // `before: FeatureFlagSnapshot?` 가 null인 CREATED 로그는 응답에서 `before` 키가 **생략**된다.
  // 기존 픽스처는 `before: null` 이라 이 계약을 재현하지 못해 운영 장애를 놓쳤다.

  it("CREATED 로그처럼 before 키가 생략된 응답도 파싱을 통과한다", () => {
    const createdLogWithoutBefore = {
      changeType: "CREATED",
      actorUserId: 1,
      actorDisplayName: "김철수",
      after: auditLog.after,
      occurredAt: "2026-07-01T00:00:00.000Z",
    };
    const data = {
      content: [createdLogWithoutBefore],
      totalElements: 1,
      totalPages: 1,
      pageNumber: 0,
      pageSize: 20,
    };

    const result = FeatureFlagAuditLogPageSchema.safeParse(data);

    expect(result.success).toBe(true);
  });

  it("before 키가 생략돼도 after는 여전히 필수다", () => {
    const logWithoutAfter = {
      changeType: "CREATED",
      actorUserId: 1,
      occurredAt: "2026-07-01T00:00:00.000Z",
    };
    const data = {
      content: [logWithoutAfter],
      totalElements: 1,
      totalPages: 1,
      pageNumber: 0,
      pageSize: 20,
    };

    expect(FeatureFlagAuditLogPageSchema.safeParse(data).success).toBe(false);
  });
});
