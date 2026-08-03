/**
 * 결제 상태 — 값 목록 · 한글 라벨 · 배지 색의 단일 출처.
 *
 * 정답은 BE `domain/payment/entity/PaymentStatus.kt` 다. FE가 일부 값(READY·CANCELLED)을
 * 몰라서 응답 파싱이 전량 실패한 이력이 있어(02-파트너포털/14 캡쳐), 값 목록을 한 곳에 모으고
 * 스키마·타입·화면이 모두 이 파일을 참조하게 한다.
 *
 * 라벨을 화면마다 따로 두면 같은 값이 어디선 "완료", 어디선 `COMPLETED`로 갈린다
 * (07-예약-관리의 `결제 상태` 컬럼이 영문 원문이던 결함). 매출·예약 화면이 이 매핑을 공유한다.
 */

/** BE PaymentStatus enum 전량. 순서는 결제 생명주기(대기 → 승인 → 완료) 순이다. */
export const PAYMENT_STATUS_VALUES = [
  "PENDING",
  "READY",
  "COMPLETED",
  "CANCELLED",
  "FAILED",
  "REFUNDED",
] as const;

export type PaymentStatus = (typeof PAYMENT_STATUS_VALUES)[number];

/** 값이 비어 있을 때 표에 채우는 자리표시자. */
const EMPTY_PLACEHOLDER = "-";

export const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  PENDING: "결제 대기",
  READY: "승인 대기",
  COMPLETED: "완료",
  CANCELLED: "취소",
  FAILED: "실패",
  REFUNDED: "환불",
};

/**
 * 상태별 배지 색. 시맨틱 토큰만 사용해 라이트·다크 두 모드에서 함께 동작한다.
 * 취소·환불은 "끝났지만 실패는 아닌" 상태라 중립색을 공유한다.
 */
const PAYMENT_STATUS_BADGE_CLASSES: Record<PaymentStatus, string> = {
  PENDING: "bg-status-warning text-status-warning-foreground",
  READY: "bg-status-info text-status-info-foreground",
  COMPLETED: "bg-status-success text-status-success-foreground",
  CANCELLED: "bg-status-neutral text-status-neutral-foreground",
  FAILED: "bg-status-danger text-status-danger-foreground",
  REFUNDED: "bg-status-neutral text-status-neutral-foreground",
};

const UNKNOWN_BADGE_CLASS = "bg-status-neutral text-status-neutral-foreground";

function isKnownPaymentStatus(value: string): value is PaymentStatus {
  return (PAYMENT_STATUS_VALUES as readonly string[]).includes(value);
}

/**
 * 결제 상태를 화면 문구로 바꾼다.
 *
 * 계약이 다시 어긋나 모르는 값이 와도 예외를 던지지 않고 원문을 그대로 돌려준다 — 표시 계층이
 * 화면 전체를 죽이는 일이 없어야 하고, 원문이 남아야 어떤 값이 새로 생겼는지 추적할 수 있다.
 */
export function paymentStatusLabel(status: string | null | undefined): string {
  if (status === null || status === undefined || status.length === 0) {
    return EMPTY_PLACEHOLDER;
  }
  return isKnownPaymentStatus(status) ? PAYMENT_STATUS_LABELS[status] : status;
}

/** 결제 상태 배지의 Tailwind 클래스. 모르는 값은 중립색으로 떨어뜨린다. */
export function paymentStatusBadgeClass(status: string): string {
  return isKnownPaymentStatus(status) ? PAYMENT_STATUS_BADGE_CLASSES[status] : UNKNOWN_BADGE_CLASS;
}
