/**
 * 결제 수단(PaymentMethod) — 값 목록 · 한글 라벨의 단일 출처.
 *
 * `결제 수단` 열이 CREDIT_CARD/KAKAO 등 영문 원문을 그대로 노출한 결함을 고치며 만들었다.
 * BE `domain/payment/vo/PaymentMethod.kt` 전량(8종)을 다루고, mobile 앱
 * (`mobile/app/payment/[id].tsx` `METHOD_LABEL`)과 값을 맞춘다.
 */
export const PAYMENT_METHOD_LABELS: Record<string, string> = {
  CREDIT_CARD: "신용카드",
  BANK_TRANSFER: "계좌이체",
  VIRTUAL_ACCOUNT: "가상계좌",
  MOBILE_PAY: "모바일결제",
  KAKAO: "카카오페이",
  TOSS: "토스페이",
  NAVER: "네이버페이",
  DANAL: "다날",
};

/** 값이 비어 있을 때 표에 채우는 자리표시자. */
const EMPTY_PLACEHOLDER = "-";

/**
 * 결제 수단을 화면 문구로 바꾼다.
 *
 * 계약 밖 값이 와도 예외를 던지지 않고 원문을 그대로 돌려준다 — 이 화면은 계약 밖 enum 값으로
 * 응답 파싱이 전량 실패한 이력이 있어(02-파트너포털 매출 화면 결함), 라벨 계층은 항상 안전한
 * fallback을 가져야 한다.
 */
export function paymentMethodLabel(method: string | null | undefined): string {
  if (method === null || method === undefined || method.length === 0) {
    return EMPTY_PLACEHOLDER;
  }
  return PAYMENT_METHOD_LABELS[method] ?? method;
}
