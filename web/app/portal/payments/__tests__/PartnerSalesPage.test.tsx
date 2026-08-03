// @vitest-environment jsdom
/**
 * 포털 "매출·결제 내역" — 파트너가 **판매한** 건의 결제를 보여준다.
 *
 * 회귀 방지: 구매자 스코프(`/payments/me`)를 호출해 파트너 본인 결제만 찾았고, 실제 결제가
 * 전부 구매자 명의라 항상 "총 0건"이었다. 판매자 스코프 응답(sales[])을 렌더해야 한다.
 *
 * 굿즈 주문에는 여러 판매자 상품이 섞일 수 있어 결제 총액과 **내 매출**을 구분해 보여준다.
 */
import { render, screen, waitFor, within } from "@testing-library/react";
import { describe, it, expect, vi, beforeEach } from "vitest";

const mockFetchPartnerSales = vi.fn();
vi.mock("@/lib/portal/payments", async (importOriginal) => {
  const actual = await importOriginal<typeof import("@/lib/portal/payments")>();
  return { ...actual, fetchPartnerSales: (params: unknown) => mockFetchPartnerSales(params) as unknown };
});

import PaymentsPage from "../page";

function buildSalesResponse(
  overrides: Partial<{ sellerAmount: number; orderType: string; pgTransactionId: string | null }> = {}
) {
  return {
    sales: [
      {
        paymentId: 15,
        orderType: "GOODS",
        orderId: 2,
        sellerAmount: 158000,
        method: "CREDIT_CARD",
        provider: "TOSS",
        status: "COMPLETED",
        paidAt: "2026-07-20T12:00:00+09:00",
        pgTransactionId: "tid-15",
        ...overrides,
      },
    ],
    totalElements: 1,
    totalPages: 1,
    page: 0,
    size: 20,
  };
}

describe("파트너 매출·결제 내역", () => {
  beforeEach(() => {
    mockFetchPartnerSales.mockReset();
  });

  it("판매한 결제 건수를 표시한다", async () => {
    mockFetchPartnerSales.mockResolvedValue(buildSalesResponse());

    render(<PaymentsPage />);

    await waitFor(() => {
      expect(screen.getByText("15")).toBeInTheDocument();
    });
    expect(screen.queryByText("결제 내역이 없습니다.")).not.toBeInTheDocument();
  });

  it("내 매출만 표시하고 결제 총액은 노출하지 않는다", async () => {
    // 결제 총액 100,000 중 내 몫은 30,000뿐인 혼합 굿즈 주문.
    // 총액을 함께 보여주면 (총액 - 내 매출)로 타 판매자 매출이 역산된다.
    mockFetchPartnerSales.mockResolvedValue(buildSalesResponse({ sellerAmount: 30000 }));

    render(<PaymentsPage />);

    const row = await screen.findByRole("row", { name: /15/ });
    expect(within(row).getByText("30,000원")).toBeInTheDocument();
    expect(within(row).queryByText("100,000원")).not.toBeInTheDocument();
  });

  it("내 매출 컬럼 헤더가 있다", async () => {
    mockFetchPartnerSales.mockResolvedValue(buildSalesResponse());

    render(<PaymentsPage />);

    await waitFor(() => {
      expect(screen.getByRole("columnheader", { name: "내 매출" })).toBeInTheDocument();
    });
    expect(screen.queryByRole("columnheader", { name: "금액" })).not.toBeInTheDocument();
  });

  it("판매 건이 없으면 빈 상태 문구를 보여준다", async () => {
    mockFetchPartnerSales.mockResolvedValue({
      sales: [],
      totalElements: 0,
      totalPages: 0,
      page: 0,
      size: 20,
    });

    render(<PaymentsPage />);

    await waitFor(() => {
      expect(screen.getByText("결제 내역이 없습니다.")).toBeInTheDocument();
    });
  });

  it("조회 실패 시 오류 메시지를 보여준다", async () => {
    mockFetchPartnerSales.mockRejectedValue(new Error("매출 내역을 불러오지 못했습니다."));

    render(<PaymentsPage />);

    await waitFor(() => {
      expect(screen.getByRole("alert")).toHaveTextContent("매출 내역을 불러오지 못했습니다.");
    });
  });
});
