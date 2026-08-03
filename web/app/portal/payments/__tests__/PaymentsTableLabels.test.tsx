// @vitest-environment jsdom
/**
 * 포털 매출 표 — 주문 유형·결제 수단이 BE enum 원문이 아니라 한글 라벨로 보여야 한다.
 *
 * 회귀 방지 1: `주문 유형`(BOOKING/TICKETING/GOODS)·`결제 수단`(CREDIT_CARD/KAKAO 등) 열이
 * 영문 enum 원문을 그대로 노출해, 이미 한글화된 다른 화면(예약 관리 등)과 표기가 어긋났다.
 * 회귀 방지 2: `PG사` 열은 `provider`(mock-pg 라우트 슬러그, BE `PaymentMethod.toPgProviderName()`
 * 에서 파생 — `backend/payment/.../vo/PaymentMethod.kt`)를 그대로 노출해 PG사(결제대행사) 이름으로
 * 오인시켰다. `provider`는 `method`의 함수일 뿐이고 심지어 다대일(CREDIT_CARD·MOBILE_PAY 모두
 * "card")이라 `method`보다 정보가 적다 — 결제 수단이 한글화된 뒤에는 열 자체가 불필요하다.
 * 회귀 방지 3: 이 화면은 직전에 계약 밖 enum 값으로 응답 파싱이 전량 실패한 이력이 있다 —
 * 라벨 매핑은 모르는 값이 와도 원문 그대로 떨어뜨려 화면을 죽이지 않아야 한다.
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
  overrides: Partial<{ orderType: string; method: string; provider: string | null }> = {}
) {
  return {
    sales: [
      {
        paymentId: 15,
        orderType: "GOODS",
        orderId: 2,
        sellerAmount: 158000,
        method: "CREDIT_CARD",
        provider: "card",
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

describe("매출 표 — 주문 유형·결제 수단 라벨", () => {
  beforeEach(() => {
    mockFetchPartnerSales.mockReset();
  });

  it("주문 유형을 한글로 보여준다 (GOODS → 상품)", async () => {
    mockFetchPartnerSales.mockResolvedValue(buildSalesResponse({ orderType: "GOODS" }));

    render(<PaymentsPage />);

    const table = await screen.findByRole("table");
    expect(within(table).getByText("상품")).toBeInTheDocument();
    expect(within(table).queryByText("GOODS")).not.toBeInTheDocument();
  });

  it("주문 유형 BOOKING·TICKETING도 한글로 보여준다", async () => {
    mockFetchPartnerSales.mockResolvedValue({
      sales: [
        { paymentId: 1, orderType: "BOOKING", orderId: 1, sellerAmount: 1000, method: "CREDIT_CARD", provider: "card", status: "COMPLETED", paidAt: null, pgTransactionId: null },
        { paymentId: 2, orderType: "TICKETING", orderId: 2, sellerAmount: 2000, method: "CREDIT_CARD", provider: "card", status: "COMPLETED", paidAt: null, pgTransactionId: null },
      ],
      totalElements: 2,
      totalPages: 1,
      page: 0,
      size: 20,
    });

    render(<PaymentsPage />);

    const table = await screen.findByRole("table");
    expect(within(table).getByText("예약")).toBeInTheDocument();
    expect(within(table).getByText("티켓")).toBeInTheDocument();
    expect(within(table).queryByText("BOOKING")).not.toBeInTheDocument();
    expect(within(table).queryByText("TICKETING")).not.toBeInTheDocument();
  });

  it("결제 수단을 한글로 보여준다 (KAKAO → 카카오페이)", async () => {
    mockFetchPartnerSales.mockResolvedValue(
      buildSalesResponse({ method: "KAKAO", provider: "kakao" })
    );

    render(<PaymentsPage />);

    const table = await screen.findByRole("table");
    expect(within(table).getByText("카카오페이")).toBeInTheDocument();
    expect(within(table).queryByText("KAKAO")).not.toBeInTheDocument();
  });

  it("모르는 주문 유형·결제 수단이 와도 원문을 노출하며 화면이 죽지 않는다", async () => {
    mockFetchPartnerSales.mockResolvedValue(
      buildSalesResponse({ orderType: "RECRUITMENT", method: "NEW_PAY" })
    );

    render(<PaymentsPage />);

    const table = await screen.findByRole("table");
    expect(within(table).getByText("RECRUITMENT")).toBeInTheDocument();
    expect(within(table).getByText("NEW_PAY")).toBeInTheDocument();
  });

  it("PG사 열을 더 이상 보여주지 않는다 (결제 수단과 중복 정보)", async () => {
    mockFetchPartnerSales.mockResolvedValue(buildSalesResponse());

    render(<PaymentsPage />);

    await waitFor(() => {
      expect(screen.getByRole("table")).toBeInTheDocument();
    });
    expect(screen.queryByRole("columnheader", { name: "PG사" })).not.toBeInTheDocument();
    expect(screen.queryByText("card")).not.toBeInTheDocument();
  });
});
