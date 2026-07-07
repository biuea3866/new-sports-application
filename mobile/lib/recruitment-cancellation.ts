/**
 * recruitment-cancellation — 신청 취소 단계 수수료 미리보기 순수 계산 (A-R6).
 *
 * 근거: `20260707-...-design-fe-app.md` Terminology "단계 수수료"(7일 초과 0% /
 * 3~7일 5% / 3일 이내 10%), "상태관리 설계"("수수료율 SSOT는 BE CancellationPolicy").
 *
 * 이 계산은 바텀시트에 미리 보여주는 값일 뿐이며, 실제 공제·환불액은 서버 확정값을
 * 따른다 — 화면은 `POST /applications/{id}/cancel` 응답을 최종값으로 취급한다.
 */
const MS_PER_DAY = 1000 * 60 * 60 * 24;

const FREE_THRESHOLD_DAYS = 7;
const STANDARD_THRESHOLD_DAYS = 3;
const STANDARD_FEE_RATE = 0.05;
const LATE_FEE_RATE = 0.1;

export type CancellationTier = 'FREE' | 'STANDARD' | 'LATE' | 'CLOSED';

export interface CancellationPreview {
  tier: CancellationTier;
  feeRate: number;
  deductedAmount: number;
  refundAmount: number;
  isCancellable: boolean;
  daysRemaining: number;
}

function resolveTier(daysRemaining: number): { tier: CancellationTier; feeRate: number } {
  if (daysRemaining > FREE_THRESHOLD_DAYS) {
    return { tier: 'FREE', feeRate: 0 };
  }
  if (daysRemaining >= STANDARD_THRESHOLD_DAYS) {
    return { tier: 'STANDARD', feeRate: STANDARD_FEE_RATE };
  }
  return { tier: 'LATE', feeRate: LATE_FEE_RATE };
}

/**
 * 참가비·신청마감·현재 시각으로 취소 시 환불 예정액을 계산한다.
 * 마감이 이미 지났으면 취소 불가(`CLOSED`)로 판정한다.
 */
export function calculateCancellationPreview(
  feeAmount: number,
  applicationDeadline: string,
  now: Date = new Date()
): CancellationPreview {
  const deadline = new Date(applicationDeadline);
  const diffMs = deadline.getTime() - now.getTime();

  if (diffMs <= 0) {
    return {
      tier: 'CLOSED',
      feeRate: 0,
      deductedAmount: 0,
      refundAmount: 0,
      isCancellable: false,
      daysRemaining: 0,
    };
  }

  const daysRemaining = Math.floor(diffMs / MS_PER_DAY);
  const { tier, feeRate } = resolveTier(daysRemaining);
  const deductedAmount = Math.round(feeAmount * feeRate);
  const refundAmount = feeAmount - deductedAmount;

  return {
    tier,
    feeRate,
    deductedAmount,
    refundAmount,
    isCancellable: true,
    daysRemaining,
  };
}
