/**
 * recruitment-format — 모집(recruitment) 화면이 공용으로 쓰는 순수 포맷 유틸.
 *
 * 근거: `20260707-모집-시설상품-소모임예약-게시글연동-design-fe-app.md` "텍스트 와이어프레임"
 * (A-R1 "정원 3 · 5,000원 · D-2 마감", A-R2 "참가비 5,000원"). 화면 컴포넌트는 렌더링에만
 * 집중하도록 금액·D-day 계산을 이 유틸로 분리한다.
 */
const MS_PER_DAY = 1000 * 60 * 60 * 24;
const DEADLINE_PASSED_LABEL = '마감';

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
    return DEADLINE_PASSED_LABEL;
  }

  const diffDays = Math.floor(diffMs / MS_PER_DAY);
  return diffDays === 0 ? 'D-day' : `D-${diffDays}`;
}

/**
 * 마감 안내 한 줄. 이미 마감된 모집에 "마감 마감"이 찍히지 않도록,
 * D-day 접미사와 "마감" 단어의 결합을 이 함수 한 곳에서 결정한다.
 */
export function formatDeadlineSummary(applicationDeadline: string, now: Date = new Date()): string {
  const dday = formatDeadlineDday(applicationDeadline, now);
  return dday === DEADLINE_PASSED_LABEL ? DEADLINE_PASSED_LABEL : `${dday} 마감`;
}

export type RecruitmentDisplayStatus = 'OPEN' | 'CLOSED' | 'CANCELLED';

/**
 * 배지에 표시할 상태. BE 상태 전이가 아직 반영되지 않아 `status`가 OPEN으로 남아 있어도
 * 신청마감이 지났으면 마감으로 표시한다 — 마감된 모집에 "모집 중" 배지가 남는 것을 막는다.
 */
export function resolveRecruitmentDisplayStatus(
  recruitment: { status: RecruitmentDisplayStatus; applicationDeadline: string },
  now: Date = new Date()
): RecruitmentDisplayStatus {
  if (recruitment.status !== 'OPEN') {
    return recruitment.status;
  }
  return formatDeadlineDday(recruitment.applicationDeadline, now) === DEADLINE_PASSED_LABEL
    ? 'CLOSED'
    : 'OPEN';
}

const INVALID_DATE_LABEL = '-';

/**
 * 날짜·시각 표기. `toLocaleString('ko-KR')` 기본값은 초까지 노출해
 * "2026. 8. 9. 오후 8:00:00"처럼 사람이 읽을 필요 없는 정보가 붙는다 — 분까지만 표기한다.
 */
export function formatDateTimeMinute(iso: string): string {
  const parsed = new Date(iso);
  if (Number.isNaN(parsed.getTime())) {
    return INVALID_DATE_LABEL;
  }
  return parsed.toLocaleString('ko-KR', {
    year: 'numeric',
    month: 'long',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });
}

/** `RecruitmentResponse.status`에 대한 한글 배지 라벨. */
export const RECRUITMENT_STATUS_LABEL: Record<RecruitmentDisplayStatus, string> = {
  OPEN: '모집 중',
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
