/**
 * 피처 플래그 BFF fetch 함수 테스트.
 * BFF 응답을 zod로 파싱해 좁히고, 실패 응답은 사용자 메시지 Error로 변환하는지 검증한다.
 */
import { describe, it, expect, vi, beforeEach } from "vitest";

import {
  fetchFeatureFlags,
  createFeatureFlag,
  fetchFlagAuditLogs,
  toAuditLogPageView,
} from "../api";
import type { FeatureFlagResponse, FeatureFlagAuditLogResponse } from "../schemas";

const mockFetch = vi.fn();
vi.stubGlobal("fetch", mockFetch);

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}

const sampleFlag: FeatureFlagResponse = {
  id: 1,
  key: "demo.feature.hello",
  type: "RELEASE",
  status: "ACTIVE",
  description: "데모 플래그",
  strategy: { strategyType: "GLOBAL_TOGGLE", enabled: true },
  createdAt: "2026-07-01T00:00:00.000Z",
  updatedAt: "2026-07-01T00:00:00.000Z",
};

const sampleAuditLog: FeatureFlagAuditLogResponse = {
  changeType: "CREATED",
  actorUserId: 12,
  before: null,
  after: {
    key: "demo.feature.hello",
    type: "RELEASE",
    status: "ACTIVE",
    description: "데모 플래그",
    strategy: { strategyType: "GLOBAL_TOGGLE", enabled: true },
  },
  occurredAt: "2026-07-03T09:50:00.000Z",
};

describe("fetchFeatureFlags", () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it("성공 시 zod 파싱된 배열을 반환한다", async () => {
    mockFetch.mockResolvedValue(jsonResponse([sampleFlag]));

    const result = await fetchFeatureFlags();

    expect(mockFetch).toHaveBeenCalledWith(
      "/api/admin/feature-flags",
      expect.objectContaining({ cache: "no-store" })
    );
    expect(result).toEqual([sampleFlag]);
  });

  it("status·type 필터를 쿼리스트링으로 전달한다", async () => {
    mockFetch.mockResolvedValue(jsonResponse([]));

    await fetchFeatureFlags({ status: "ARCHIVED", type: "RELEASE" });

    const url = (mockFetch.mock.calls[0] as [string])[0];
    expect(url).toContain("status=ARCHIVED");
    expect(url).toContain("type=RELEASE");
  });

  it("wire가 {content:[...]} 형태여도 배열로 흡수해 반환한다", async () => {
    mockFetch.mockResolvedValue(jsonResponse({ content: [sampleFlag], totalElements: 1 }));

    const result = await fetchFeatureFlags();

    expect(result).toEqual([sampleFlag]);
  });

  it("BFF가 5xx를 반환하면 사용자 메시지 Error를 throw한다", async () => {
    mockFetch.mockResolvedValue(jsonResponse({ message: "서버 오류가 발생했습니다." }, 500));

    await expect(fetchFeatureFlags()).rejects.toThrow("서버 오류가 발생했습니다.");
  });

  it("첫 원소의 description이 null이어도 목록 전체를 정상 파싱해 반환한다", async () => {
    const flagWithoutDescription = { ...sampleFlag, description: null };
    mockFetch.mockResolvedValue(jsonResponse([flagWithoutDescription, sampleFlag]));

    const result = await fetchFeatureFlags();

    expect(result).toEqual([flagWithoutDescription, sampleFlag]);
  });
});

describe("createFeatureFlag", () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it("400(key 중복) 응답의 message를 담은 Error를 throw한다", async () => {
    mockFetch.mockResolvedValue(
      jsonResponse({ message: "이미 존재하는 key입니다." }, 400)
    );

    await expect(
      createFeatureFlag({
        key: "demo.feature.hello",
        type: "RELEASE",
        description: "중복 키",
        strategy: { strategyType: "GLOBAL_TOGGLE", enabled: false },
      })
    ).rejects.toThrow("이미 존재하는 key입니다.");
  });

  it("성공 시 zod 파싱된 응답을 반환한다", async () => {
    mockFetch.mockResolvedValue(jsonResponse(sampleFlag, 201));

    const result = await createFeatureFlag({
      key: "demo.feature.hello",
      type: "RELEASE",
      description: "데모 플래그",
      strategy: { strategyType: "GLOBAL_TOGGLE", enabled: true },
    });

    expect(result).toEqual(sampleFlag);
  });
});

describe("fetchFlagAuditLogs", () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it("wire 응답을 canonical {logs,total,page,size,totalPages}로 정규화한다", async () => {
    mockFetch.mockResolvedValue(
      jsonResponse({
        content: [sampleAuditLog],
        totalElements: 25,
        totalPages: 3,
        pageNumber: 0,
        pageSize: 10,
      })
    );

    const result = await fetchFlagAuditLogs("demo.feature.hello", 0, 10);

    expect(mockFetch).toHaveBeenCalledWith(
      "/api/admin/feature-flags/demo.feature.hello/audit-logs?page=0&size=10",
      expect.objectContaining({ cache: "no-store" })
    );
    expect(result).toEqual({
      logs: [sampleAuditLog],
      total: 25,
      page: 0,
      size: 10,
      totalPages: 3,
    });
  });

  it("404 응답이면 사용자 메시지 Error를 throw한다", async () => {
    mockFetch.mockResolvedValue(
      jsonResponse({ message: "요청한 리소스를 찾을 수 없습니다." }, 404)
    );

    await expect(fetchFlagAuditLogs("unknown-flag")).rejects.toThrow(
      "요청한 리소스를 찾을 수 없습니다."
    );
  });
});

describe("toAuditLogPageView", () => {
  it("wire에 totalPages가 없으면 total·size로 totalPages를 계산한다", () => {
    const result = toAuditLogPageView({
      content: [sampleAuditLog],
      totalElements: 25,
      pageNumber: 0,
      pageSize: 10,
    });

    expect(result.totalPages).toBe(3);
  });

  it("wire에 totalPages가 있으면 그 값을 그대로 사용한다", () => {
    const result = toAuditLogPageView({
      content: [],
      totalElements: 0,
      totalPages: 0,
      pageNumber: 0,
      pageSize: 10,
    });

    expect(result.totalPages).toBe(0);
  });
});
