// @vitest-environment jsdom
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import type { FeatureFlagAuditLogResponse } from "@/lib/admin/feature-flags/schemas";
import { AuditLogTable } from "../AuditLogTable";

const ARCHIVED_LOG: FeatureFlagAuditLogResponse = {
  changeType: "ARCHIVED",
  actorUserId: 12,
  before: {
    key: "demo.feature.hello",
    type: "RELEASE",
    status: "ACTIVE",
    description: "데모 인사 엔드포인트 킬스위치",
    strategy: { strategyType: "GLOBAL_TOGGLE", enabled: true },
  },
  after: {
    key: "demo.feature.hello",
    type: "RELEASE",
    status: "ARCHIVED",
    description: "데모 인사 엔드포인트 킬스위치",
    strategy: { strategyType: "GLOBAL_TOGGLE", enabled: true },
  },
  occurredAt: "2026-07-03T14:20:00Z",
};

const CREATED_LOG: FeatureFlagAuditLogResponse = {
  changeType: "CREATED",
  actorUserId: 12,
  before: null,
  after: {
    key: "demo.feature.hello",
    type: "RELEASE",
    status: "ACTIVE",
    description: "데모 인사 엔드포인트 킬스위치",
    strategy: { strategyType: "GLOBAL_TOGGLE", enabled: false },
  },
  occurredAt: "2026-07-03T09:50:00Z",
};

describe("AuditLogTable", () => {
  it("각 행에 변경 유형 배지·변경자·before→after 요약이 렌더된다", () => {
    render(<AuditLogTable logs={[ARCHIVED_LOG]} />);

    expect(screen.getByText("ARCHIVED")).toBeInTheDocument();
    expect(screen.getByText("#12")).toBeInTheDocument();
    expect(screen.getAllByText("전역 ON").length).toBeGreaterThan(0);
  });

  it("before가 null인 행은 (없음) → 이후 요약으로 표시된다", () => {
    render(<AuditLogTable logs={[CREATED_LOG]} />);

    expect(screen.getByText("(없음)")).toBeInTheDocument();
    expect(screen.getByText("전역 OFF")).toBeInTheDocument();
  });
});
