// @vitest-environment jsdom
/**
 * 운영 인사이트 굿즈 KPI — 라벨과 단위가 실제 계산 결과와 일치해야 한다.
 *
 * 회귀 방지 1: "재고 회전율"로 표시하던 값이 실제로는 `기간매출 ÷ 활성상품수` = 상품당 평균
 * 매출액(원)이었다.
 * 회귀 방지 2: "일 매출 합계"로 표시하던 값이 실제로는 `기간매출 ÷ 기간일수`(GoodsDomainService
 * #aggregateGoodsKpi) = 일 평균 매출액(원)이었다. "합계"가 아니라 "평균"이다 — 두 칸 모두
 * 평균이므로 무엇의 평균인지(일별 vs 상품당) 라벨로 구분한다. 그 "평균" 자체도 분모가
 * off-by-one(ChronoUnit.DAYS.between이 종료일 미포함인데 매출 합산 창은 양끝 포함)으로
 * 체계적으로 과대됐던 결함을 BE에서 함께 고쳤다(GoodsDomainServiceTest 참조).
 * 회귀 방지 3: 통화 값이 소수점 2자리까지 찍혀(`26,333.33원`) 원 단위인데 소수가 보였고, 그
 * 결과 "일 매출 합계"·"상품당 평균 매출" 두 칸이 같은 값(`26333.33`)일 때 시각적으로도
 * 동일해 보여 "합계 칸이 실은 평균 아니냐"는 의심을 낳았다. 원은 소수 단위가 없다.
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

  it("일 평균 매출로 표시한다 (일 매출 합계 아님)", async () => {
    mockFetchOperationKpi.mockResolvedValue(
      buildKpi({ dailyRevenueTotal: 26333.33, inventoryTurnoverRate: 26333.33 })
    );

    render(<InsightsPage />);

    await waitFor(() => {
      expect(screen.getByText("일 평균 매출")).toBeInTheDocument();
    });
    expect(screen.queryByText("일 매출 합계")).not.toBeInTheDocument();
  });

  it("일 평균 매출과 상품당 평균 매출을 서로 다른 라벨로 구분한다", async () => {
    mockFetchOperationKpi.mockResolvedValue(
      buildKpi({ dailyRevenueTotal: 26333.33, inventoryTurnoverRate: 26333.33 })
    );

    render(<InsightsPage />);

    const dailyCard = await screen.findByLabelText("일 평균 매출 지표");
    const perProductCard = await screen.findByLabelText("상품당 평균 매출 지표");
    expect(dailyCard).not.toBe(perProductCard);
  });

  it("통화 값에 원 단위와 천단위 구분을 적용하고 소수는 표시하지 않는다", async () => {
    mockFetchOperationKpi.mockResolvedValue(
      buildKpi({ dailyRevenueTotal: 26333.33, inventoryTurnoverRate: 26333.33 })
    );

    render(<InsightsPage />);

    const card = await screen.findByLabelText("상품당 평균 매출 지표");
    expect(within(card).getByText("26,333원")).toBeInTheDocument();
    expect(within(card).queryByText("26,333.33원")).not.toBeInTheDocument();
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

  it("소수점 이하는 반올림해서 표시한다 (0.5원 → 1원)", async () => {
    mockFetchOperationKpi.mockResolvedValue(
      buildKpi({ dailyRevenueTotal: 0.5, inventoryTurnoverRate: 0.5 })
    );

    render(<InsightsPage />);

    const card = await screen.findByLabelText("상품당 평균 매출 지표");
    expect(within(card).getByText("1원")).toBeInTheDocument();
  });
});
