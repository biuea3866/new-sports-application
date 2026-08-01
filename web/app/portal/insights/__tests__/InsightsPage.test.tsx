// @vitest-environment jsdom
/**
 * 운영 인사이트 화면.
 *
 * 회귀 방지: "인기 시설 TOP5"가 시설명 대신 Mongo ObjectId를 그대로 노출해
 * `시설 #6a6c334c3fc5f44fbb2c26d8`로 보였다. 목록은 사람이 읽는 이름을 렌더해야 한다.
 */
import { render, screen, waitFor } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockFetchOperationKpi = vi.fn();
vi.mock("@/lib/portal/operationKpi", () => ({
  fetchOperationKpi: (params: unknown) => mockFetchOperationKpi(params) as unknown,
}));

import InsightsPage from "../page";

function buildKpi(topFacilities: { id: string; name: string }[]) {
  return {
    ownerUserId: 10,
    facility: { utilizationRate: 1.0, noShowRate: 0, topFacilities },
    goods: { dailyRevenueTotal: 26333.33, inventoryTurnoverRate: 26333.33, outOfStockSkuCount: 0 },
    ticket: { totalSoldCount: 2, refundRate: 0, complimentaryCount: 0 },
  };
}

describe("운영 인사이트 — 인기 시설 TOP5", () => {
  beforeEach(() => {
    mockFetchOperationKpi.mockReset();
  });

  it("시설명을 표시한다", async () => {
    mockFetchOperationKpi.mockResolvedValue(
      buildKpi([{ id: "6a6c334c3fc5f44fbb2c26d8", name: "강남 스포츠센터" }])
    );

    render(<InsightsPage />);

    await waitFor(() => {
      expect(screen.getByText("강남 스포츠센터")).toBeInTheDocument();
    });
  });

  it("내부 식별자(ObjectId)를 화면에 노출하지 않는다", async () => {
    mockFetchOperationKpi.mockResolvedValue(
      buildKpi([{ id: "6a6c334c3fc5f44fbb2c26d8", name: "강남 스포츠센터" }])
    );

    render(<InsightsPage />);

    await waitFor(() => {
      expect(screen.getByText("강남 스포츠센터")).toBeInTheDocument();
    });
    expect(screen.queryByText(/6a6c334c3fc5f44fbb2c26d8/)).not.toBeInTheDocument();
  });

  it("인기 시설이 없으면 빈 상태 문구를 보여준다", async () => {
    mockFetchOperationKpi.mockResolvedValue(buildKpi([]));

    render(<InsightsPage />);

    await waitFor(() => {
      expect(screen.getByText("데이터가 없습니다.")).toBeInTheDocument();
    });
  });

  it("조회 실패 시 오류 메시지를 보여준다", async () => {
    mockFetchOperationKpi.mockRejectedValue(new Error("KPI 데이터를 불러오지 못했습니다."));

    render(<InsightsPage />);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("KPI 데이터를 불러오지 못했습니다.");
    });
  });
});
