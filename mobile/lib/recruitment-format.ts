/**
 * recruitment-format — 모집(recruitment) 화면이 공용으로 쓰는 순수 포맷 유틸.
 *
 * 근거: `20260707-모집-시설상품-소모임예약-게시글연동-design-fe-app.md` "텍스트 와이어프레임"
 * (A-R1 "정원 3 · 5,000원 · D-2 마감", A-R2 "참가비 5,000원"). 화면 컴포넌트는 렌더링에만
 * 집중하도록 금액·D-day 계산을 이 유틸로 분리한다.
 */
const MS_PER_DAY = 1000 * 60 * 60 * 24;

/** 참가비를 표시 문자열로 변환한다. 0원은 "무료"로 표기한다. */
export function formatFeeAmount(feeAmount: number): string {
  return feeAmount === 0 ? '무료' : `${feeAmount.toLocaleString()}원`;
}

/**
 * 신청마감까지 남은 일수를 "D-N" 형식으로 반환한다. 마감이 지났으면 "마감",
 * 당일이면 "D-day"를 반환한다.
 */
export function formatDeadlineDday(applicationDeadline: string, now: Date = new Date()): string {
  const deadline = new Date(applicationDeadline);
  const diffMs = deadline.getTime() - now.getTime();

  if (diffMs <= 0) {
    return '마감';
  }

  const diffDays = Math.floor(diffMs / MS_PER_DAY);
  return diffDays === 0 ? 'D-day' : `D-${diffDays}`;
}

/** `RecruitmentResponse.status`에 대한 한글 배지 라벨. */
export const RECRUITMENT_STATUS_LABEL: Record<'OPEN' | 'CLOSED' | 'CANCELLED', string> = {
  OPEN: 'OPEN',
  CLOSED: '마감됨',
  CANCELLED: '취소됨',
};

/** `ApplicationStatus`에 대한 한글 배지 라벨. */
export const APPLICATION_STATUS_LABEL: Record<
  'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'REFUNDED',
  string
> = {
  PENDING: '대기 중',
  CONFIRMED: '확정',
  CANCELLED: '취소됨',
  REFUNDED: '환불됨',
};

/** 신청 취소 CTA를 노출할 수 있는 상태인지 — 이미 종료된 신청은 재취소 대상이 아니다. */
export function isApplicationCancellable(
  status: 'PENDING' | 'CONFIRMED' | 'CANCELLED' | 'REFUNDED'
): boolean {
  return status === 'PENDING' || status === 'CONFIRMED';
}
