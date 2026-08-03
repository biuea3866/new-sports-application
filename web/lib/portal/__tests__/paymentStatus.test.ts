/**
 * 결제 상태 계약 테스트 — BE `PaymentStatus` enum과 FE 표현이 어긋나지 않는지 고정한다.
 *
 * 회귀 방지: FE가 4종(PENDING/COMPLETED/FAILED/REFUNDED)만 알고 있어 실제 데이터의
 * `READY` 결제에서 `PartnerSalesResponseSchema.parse`가 전량 실패했다. 그 결과 매출 화면이
 * "총 0건" + Zod issue 배열 원문 노출이라는 이중 결함으로 나타났다(02-파트너포털/14 캡쳐).
 * BE가 계약의 정답이므로 6종 전부를 파싱하고, 전부 한글 라벨을 가져야 한다.
 */
import { describe, it, expect } from "vitest";
import {
  PAYMENT_STATUS_VALUES,
  PAYMENT_STATUS_LABELS,
  paymentStatusLabel,
  paymentStatusBadgeClass,
  type PaymentStatus,
} from "../paymentStatus";
import { PartnerSalesResponseSchema } from "../schemas";

/** BE `domain/payment/entity/PaymentStatus.kt` 의 전량. 이 목록이 계약의 정답이다. */
const BACKEND_PAYMENT_STATUSES = [
  "PENDING",
  "READY",
  "COMPLETED",
  "CANCELLED",
  "FAILED",
  "REFUNDED",
] as const;

function buildSale(status: string) {
  return {
    paymentId: 1,
    orderType: "BOOKING",
    orderId: 2,
    sellerAmount: 30000,
    method: "CREDIT_CARD",
    provider: "TOSS",
    status,
    paidAt: "2026-07-20T12:00:00+09:00",
    pgTransactionId: "tid-1",
  };
}

function buildResponse(statuses: readonly string[]) {
  return {
    sales: statuses.map(buildSale),
    totalElements: statuses.length,
    totalPages: 1,
    page: 0,
    size: 20,
  };
}

describe("결제 상태 계약", () => {
  it("BE PaymentStatus enum 6종을 빠짐없이 안다", () => {
    expect([...PAYMENT_STATUS_VALUES].sort()).toEqual([...BACKEND_PAYMENT_STATUSES].sort());
  });

  it("매출 응답 스키마가 BE 상태 6종을 모두 파싱한다", () => {
    const result = PartnerSalesResponseSchema.safeParse(buildResponse(BACKEND_PAYMENT_STATUSES));

    expect(result.success).toBe(true);
  });

  // READY 가 실제 시드 데이터에 존재해 전량 파싱 실패를 유발했던 값이다.
  it("READY 상태 결제 한 건만 있어도 파싱에 성공한다", () => {
    const result = PartnerSalesResponseSchema.safeParse(buildResponse(["READY"]));

    expect(result.success).toBe(true);
  });

  /**
   * 목록 응답은 통째로 파싱된다 — 계약 밖 상태 하나에 `parse`가 throw하면 화면이 다시 `총 0건`이
   * 된다(이번 결함과 같은 실패 모드). enum을 넓히기만 하면 다음 값에서 반복되므로, 미지 값은
   * **그 행만** 원문으로 남기고 나머지 행은 살린다.
   */
  it("계약 밖 상태가 섞여도 파싱에 성공하고 그 값은 원문으로 남는다", () => {
    const result = PartnerSalesResponseSchema.safeParse(
      buildResponse(["COMPLETED", "UNKNOWN_STATUS", "READY"])
    );

    expect(result.success).toBe(true);
    expect(result.data?.sales.map((sale) => sale.status)).toEqual([
      "COMPLETED",
      "UNKNOWN_STATUS",
      "READY",
    ]);
  });

  // 내성은 상태값에만 준다 — 구조가 깨진 응답은 계속 즉시 실패해야 한다.
  it("상태가 문자열이 아니면 파싱에 실패한다", () => {
    const result = PartnerSalesResponseSchema.safeParse({
      sales: [{ ...buildSale("COMPLETED"), status: 42 }],
      totalElements: 1,
      totalPages: 1,
      page: 0,
      size: 20,
    });

    expect(result.success).toBe(false);
  });
});

describe("결제 상태 한글 라벨", () => {
  it("모든 상태에 한글 라벨이 있다", () => {
    for (const status of PAYMENT_STATUS_VALUES) {
      const label = PAYMENT_STATUS_LABELS[status];
      expect(label.length).toBeGreaterThan(0);
      // 영문 원문(PENDING 등)이 그대로 노출되면 안 된다.
      expect(label).not.toMatch(/[A-Za-z]/);
    }
  });

  it("상태별로 서로 다른 라벨을 쓴다", () => {
    const labels = PAYMENT_STATUS_VALUES.map((status) => PAYMENT_STATUS_LABELS[status]);

    expect(new Set(labels).size).toBe(labels.length);
  });

  it("알려진 상태 문자열을 한글 라벨로 바꾼다", () => {
    expect(paymentStatusLabel("COMPLETED")).toBe(PAYMENT_STATUS_LABELS.COMPLETED);
    expect(paymentStatusLabel("READY")).toBe(PAYMENT_STATUS_LABELS.READY);
  });

  // 계약이 다시 어긋나도 화면이 죽지 않아야 한다 — 라벨은 표시 계층의 마지막 방어선이다.
  it("모르는 상태는 원문을 그대로 돌려주고 예외를 던지지 않는다", () => {
    expect(() => paymentStatusLabel("SOMETHING_NEW")).not.toThrow();
    expect(paymentStatusLabel("SOMETHING_NEW")).toBe("SOMETHING_NEW");
  });

  it("값이 없으면 자리표시자를 돌려준다", () => {
    expect(paymentStatusLabel(null)).toBe("-");
    expect(paymentStatusLabel(undefined)).toBe("-");
  });

  it("모르는 상태에도 배지 클래스를 돌려준다", () => {
    expect(paymentStatusBadgeClass("SOMETHING_NEW").length).toBeGreaterThan(0);
  });

  it("배지 클래스는 시맨틱 토큰만 쓴다", () => {
    const classes = PAYMENT_STATUS_VALUES.map((status: PaymentStatus) =>
      paymentStatusBadgeClass(status)
    );

    for (const className of classes) {
      // 원색 유틸리티(bg-red-500 등)·hex 하드코딩 금지 — status-* 시맨틱 토큰만 허용한다.
      expect(className).toMatch(/status-/);
      expect(className).not.toMatch(/#[0-9a-fA-F]{3,6}/);
    }
  });
});
