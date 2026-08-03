/**
 * 결제 수단(PaymentMethod) 라벨 테스트.
 *
 * 회귀 방지(p3-2): 화면 테스트만으로는 `PAYMENT_METHOD_LABELS`의 전체 매핑·빈 값(`EMPTY_
 * PLACEHOLDER`) 경로가 실제로 검증되지 않는다. `PartnerSaleSchema.method`는 `z.string()`이라
 * 빈 문자열도 계약상 도달 가능하다. 유틸 단위 테스트가 화면 테스트보다 싸고 빠르다.
 */
import { describe, it, expect } from "vitest";
import { PAYMENT_METHOD_LABELS, paymentMethodLabel } from "../paymentMethod";

/** BE `domain/payment/vo/PaymentMethod.kt` 의 전량. */
const BACKEND_PAYMENT_METHODS = [
  "CREDIT_CARD",
  "BANK_TRANSFER",
  "VIRTUAL_ACCOUNT",
  "MOBILE_PAY",
  "KAKAO",
  "TOSS",
  "NAVER",
  "DANAL",
] as const;

describe("결제 수단 한글 라벨", () => {
  it("BE PaymentMethod 8종을 빠짐없이 안다", () => {
    expect(Object.keys(PAYMENT_METHOD_LABELS).sort()).toEqual(
      [...BACKEND_PAYMENT_METHODS].sort()
    );
  });

  it("모든 결제 수단에 한글 라벨이 있다", () => {
    for (const method of BACKEND_PAYMENT_METHODS) {
      const label = paymentMethodLabel(method);
      expect(label.length).toBeGreaterThan(0);
      expect(label).not.toMatch(/[A-Za-z]/);
    }
  });

  it("결제 수단별로 서로 다른 라벨을 쓴다", () => {
    const labels = BACKEND_PAYMENT_METHODS.map((method) => paymentMethodLabel(method));
    expect(new Set(labels).size).toBe(labels.length);
  });

  it("알려진 결제 수단을 한글 라벨로 바꾼다", () => {
    expect(paymentMethodLabel("CREDIT_CARD")).toBe("신용카드");
    expect(paymentMethodLabel("KAKAO")).toBe("카카오페이");
  });

  it("모르는 결제 수단은 원문을 그대로 돌려주고 예외를 던지지 않는다", () => {
    expect(() => paymentMethodLabel("NEW_PAY")).not.toThrow();
    expect(paymentMethodLabel("NEW_PAY")).toBe("NEW_PAY");
  });

  it("값이 없거나 빈 문자열이면 자리표시자를 돌려준다", () => {
    expect(paymentMethodLabel(null)).toBe("-");
    expect(paymentMethodLabel(undefined)).toBe("-");
    expect(paymentMethodLabel("")).toBe("-");
  });
});
