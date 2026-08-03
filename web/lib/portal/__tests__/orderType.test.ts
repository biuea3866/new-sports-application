/**
 * 주문 유형(OrderType) 계약·라벨 테스트.
 *
 * 회귀 방지(p1-2): `OrderTypeSchema`를 BOOKING/TICKETING/GOODS 3종 enum으로 좁혀 두면 계약 밖
 * `orderType` 1건에 `PartnerSalesResponseSchema.parse`가 목록 전체를 throw한다 — `status`가
 * `READY` 한 종을 몰라 겪은 것과 동일한 실패 모드다(02-파트너포털/14). 이 테스트는 실제 zod
 * 스키마(`.safeParse`)를 통과시켜 검증한다 — 컴포넌트 테스트처럼 `fetchPartnerSales`를 모킹해
 * Zod를 우회하면 이 회귀를 못 잡는다.
 *
 * BE `common/.../order/OrderType.kt`는 4종(BOOKING·TICKETING·GOODS·RECRUITMENT)이 정답이다.
 */
import { describe, it, expect } from "vitest";
import { PartnerSalesResponseSchema } from "../schemas";
import { ORDER_TYPE_LABELS, orderTypeLabel } from "../orderType";

/** BE `common/.../order/OrderType.kt` 의 전량. 이 목록이 계약의 정답이다. */
const BACKEND_ORDER_TYPES = ["BOOKING", "TICKETING", "GOODS", "RECRUITMENT"] as const;

function buildSale(orderType: string) {
  return {
    paymentId: 1,
    orderType,
    orderId: 2,
    sellerAmount: 30000,
    method: "CREDIT_CARD",
    provider: "card",
    status: "COMPLETED",
    paidAt: "2026-07-20T12:00:00+09:00",
    pgTransactionId: "tid-1",
  };
}

function buildResponse(orderTypes: readonly string[]) {
  return {
    sales: orderTypes.map(buildSale),
    totalElements: orderTypes.length,
    totalPages: 1,
    page: 0,
    size: 20,
  };
}

describe("주문 유형 계약", () => {
  it("BE OrderType 4종을 빠짐없이 안다", () => {
    expect(Object.keys(ORDER_TYPE_LABELS).sort()).toEqual([...BACKEND_ORDER_TYPES].sort());
  });

  it("매출 응답 스키마가 BE 주문 유형 4종을 모두 파싱한다", () => {
    const result = PartnerSalesResponseSchema.safeParse(buildResponse(BACKEND_ORDER_TYPES));

    expect(result.success).toBe(true);
  });

  // 계약 밖 orderType 하나에 목록 전체가 throw하면 화면이 다시 "총 0건"이 된다.
  it("계약 밖 주문 유형이 섞여도 파싱에 성공하고 그 값은 원문으로 남는다", () => {
    const result = PartnerSalesResponseSchema.safeParse(
      buildResponse(["GOODS", "SUBSCRIPTION", "BOOKING"])
    );

    expect(result.success).toBe(true);
    expect(result.data?.sales.map((sale) => sale.orderType)).toEqual([
      "GOODS",
      "SUBSCRIPTION",
      "BOOKING",
    ]);
  });
});

describe("주문 유형 한글 라벨", () => {
  it("모든 주문 유형에 한글 라벨이 있다", () => {
    for (const orderType of BACKEND_ORDER_TYPES) {
      const label = orderTypeLabel(orderType);
      expect(label.length).toBeGreaterThan(0);
      expect(label).not.toMatch(/[A-Za-z]/);
    }
  });

  it("주문 유형별로 서로 다른 라벨을 쓴다", () => {
    const labels = BACKEND_ORDER_TYPES.map((orderType) => orderTypeLabel(orderType));
    expect(new Set(labels).size).toBe(labels.length);
  });

  it("알려진 주문 유형을 한글 라벨로 바꾼다", () => {
    expect(orderTypeLabel("BOOKING")).toBe("예약");
    expect(orderTypeLabel("TICKETING")).toBe("티켓");
    expect(orderTypeLabel("GOODS")).toBe("상품");
    expect(orderTypeLabel("RECRUITMENT")).toBe("모집");
  });

  it("모르는 주문 유형은 원문을 그대로 돌려주고 예외를 던지지 않는다", () => {
    expect(() => orderTypeLabel("SUBSCRIPTION")).not.toThrow();
    expect(orderTypeLabel("SUBSCRIPTION")).toBe("SUBSCRIPTION");
  });

  it("값이 없으면 자리표시자를 돌려준다", () => {
    expect(orderTypeLabel(null)).toBe("-");
    expect(orderTypeLabel(undefined)).toBe("-");
    expect(orderTypeLabel("")).toBe("-");
  });
});
