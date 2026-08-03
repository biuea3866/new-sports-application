// @vitest-environment jsdom
/**
 * 운영 인사이트 굿즈 KPI — 라벨과 단위가 실제 계산 결과와 일치해야 한다.
 *
 * 회귀 방지: "재고 회전율"로 표시하던 값이 실제로는 `기간매출 ÷ 활성상품수` = 상품당 평균
 * 매출액(원)이었다. 통화 값이 단위·천단위 구분 없이 `26333.33`으로 찍혀 옆 칸 "일 매출 합계
 * 26,333.33원"과 같은 값처럼 보였고, 두 지표가 같은 소스를 쓴다는 오해까지 낳았다.
 */
import { render, screen, waitFor, within } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockFetchOperationKpi = vi.fn();
vi.mock("@/lib/portal/operationKpi", () => ({
  fetchOperationKpi: (params: unknown) => mockFetchOperationKpi(params) as unknown,
}));

import InsightsPage from "../page";

function buildKpi(goods: { dailyRevenueTotal: number; inventoryTurnoverRate: number }) {
  return {
    ownerUserId: 69,
    facility: { utilizationRate: 1.0, noShowRate: 0, topFacilities: [] },
    goods: { ...goods, outOfStockSkuCount: 0 },
    ticket: { totalSoldCount: 2, refundRate: 0, complimentaryCount: 0 },
  };
}

describe("굿즈 KPI 라벨·단위", () => {
  beforeEach(() => {
    mockFetchOperationKpi.mockReset();
  });

  it("상품당 평균 매출로 표시한다 (재고 회전율 아님)", async () => {
    mockFetchOperationKpi.mockResolvedValue(
      buildKpi({ dailyRevenueTotal: 26333.33, inventoryTurnoverRate: 26333.33 })
    );

    render(<InsightsPage />);

    await waitFor(() => {
      expect(screen.getByText("상품당 평균 매출")).toBeInTheDocument();
    });
    expect(screen.queryByText("재고 회전율")).not.toBeInTheDocument();
  });

  it("통화 값에 원 단위와 천단위 구분을 적용한다", async () => {
    mockFetchOperationKpi.mockResolvedValue(
      buildKpi({ dailyRevenueTotal: 26333.33, inventoryTurnoverRate: 26333.33 })
    );

    render(<InsightsPage />);

    const card = await screen.findByLabelText("상품당 평균 매출 지표");
    expect(within(card).getByText("26,333.33원")).toBeInTheDocument();
  });

  it("정수 금액도 천단위 구분을 적용한다", async () => {
    mockFetchOperationKpi.mockResolvedValue(
      buildKpi({ dailyRevenueTotal: 158000, inventoryTurnoverRate: 158000 })
    );

    render(<InsightsPage />);

    const card = await screen.findByLabelText("상품당 평균 매출 지표");
    expect(within(card).getByText("158,000원")).toBeInTheDocument();
  });

  it("매출이 0이어도 원 단위를 붙여 표시한다", async () => {
    mockFetchOperationKpi.mockResolvedValue(
      buildKpi({ dailyRevenueTotal: 0, inventoryTurnoverRate: 0 })
    );

    render(<InsightsPage />);

    const card = await screen.findByLabelText("상품당 평균 매출 지표");
    expect(within(card).getByText("0원")).toBeInTheDocument();
  });
});
