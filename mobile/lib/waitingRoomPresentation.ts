/**
 * waitingRoomPresentation — 대기실 뷰모델(useWaitingRoom)이 사용하는 순수 표시 로직.
 *
 * 근거: 티켓 FE-07, `20260709-가상대기열-design-fe-app.md` "S1 텍스트 와이어프레임"(진행바 비율) ·
 * "Open Questions"(ratio 음수 clamp). no-logic-in-component — 진행바 비율·ETA 라벨 계산을
 * 훅·컴포넌트 밖 순수 함수로 분리한다.
 */

const SECONDS_PER_MINUTE = 60;

/**
 * 최초 WAITING 응답의 aheadCount(initialAheadCount) 대비 현재 aheadCount로 진행 비율을 계산한다.
 * - 기준값이 없거나(null) 0 이하이면 진행바를 표시하지 않는다(null) — design-fe-app.md "진행바 비율".
 * - 재진입자 유입 등으로 aheadCount가 기준값을 넘어서면 비율이 음수가 될 수 있어 [0,1]로 clamp한다.
 */
export function computeProgressRatio(
  aheadCount: number | null,
  initialAheadCount: number | null
): number | null {
  if (aheadCount === null || initialAheadCount === null || initialAheadCount <= 0) {
    return null;
  }
  const rawRatio = 1 - aheadCount / initialAheadCount;
  return Math.min(Math.max(rawRatio, 0), 1);
}

/** 진행 비율을 "62%" 형태의 라벨로 변환한다. ratio가 없으면 라벨도 없다. */
export function formatPercentLabel(ratio: number | null): string | null {
  if (ratio === null) {
    return null;
  }
  return `${Math.round(ratio * 100)}%`;
}

/**
 * 예상 대기 초(etaSeconds)를 "약 N분" 라벨로 변환한다.
 * 1분 미만으로 반올림되는 경우도 최소 "약 1분"으로 표시한다(0분 노출 방지).
 */
export function formatEtaLabel(etaSeconds: number | null): string | null {
  if (etaSeconds === null) {
    return null;
  }
  const minutes = Math.max(1, Math.ceil(etaSeconds / SECONDS_PER_MINUTE));
  return `약 ${minutes}분`;
}
