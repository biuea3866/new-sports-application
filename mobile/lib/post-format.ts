/**
 * post-format — 게시글·댓글 화면이 공용으로 쓰는 순수 포맷 유틸.
 *
 * 근거: `20260707-모집-시설상품-소모임예약-게시글연동-design-fe-app.md` "텍스트 와이어프레임"
 * A-P1/A-P2("홍길동 · 2일 전"). 화면 컴포넌트는 렌더링에만 집중하도록 상대시각 계산을
 * 이 유틸로 분리한다.
 */
const MS_PER_MINUTE = 1000 * 60;
const MS_PER_HOUR = MS_PER_MINUTE * 60;
const MS_PER_DAY = MS_PER_HOUR * 24;

/**
 * ISO-8601 시각을 상대 시간 문자열로 변환한다. 1분 미만은 "방금 전", 이후 분/시간/일
 * 단위로 표기하고 30일을 넘으면 절대 날짜(YYYY.MM.DD)로 표기한다.
 */
export function formatRelativeTime(iso: string, now: Date = new Date()): string {
  const target = new Date(iso);
  const diffMs = now.getTime() - target.getTime();

  if (diffMs < MS_PER_MINUTE) {
    return '방금 전';
  }
  if (diffMs < MS_PER_HOUR) {
    return `${Math.floor(diffMs / MS_PER_MINUTE)}분 전`;
  }
  if (diffMs < MS_PER_DAY) {
    return `${Math.floor(diffMs / MS_PER_HOUR)}시간 전`;
  }
  if (diffMs < MS_PER_DAY * 30) {
    return `${Math.floor(diffMs / MS_PER_DAY)}일 전`;
  }

  return `${target.getFullYear()}.${String(target.getMonth() + 1).padStart(2, '0')}.${String(
    target.getDate()
  ).padStart(2, '0')}`;
}

/** 작성자 표시 라벨. 프로필 닉네임 API가 없어 사용자 ID 기반으로 표기한다(기존 화면과 동일). */
export function formatAuthor(userId: number): string {
  return `사용자 ${userId}`;
}
