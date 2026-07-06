/**
 * 커뮤니티(동아리) 목록·개설 화면에서 쓰는 표시용 순수 매핑 — 종목 이모지/라벨, 공개여부 라벨.
 *
 * 근거: `api/community-types.ts`의 `SportCategory`(BE 확정 12개 값), design-fe-app.md S3 와이어프레임.
 * 색 값은 포함하지 않는다 — 화면이 useTheme()으로 실제 색을 해석한다.
 */
import type { CommunityVisibility, SportCategory } from '../api/community-types';

export interface SportCategoryDisplay {
  label: string;
  emoji: string;
}

const SPORT_CATEGORY_DISPLAY_MAP: Record<SportCategory, SportCategoryDisplay> = {
  SOCCER: { label: '축구', emoji: '⚽' },
  BASKETBALL: { label: '농구', emoji: '🏀' },
  BASEBALL: { label: '야구', emoji: '⚾' },
  TENNIS: { label: '테니스', emoji: '🎾' },
  BADMINTON: { label: '배드민턴', emoji: '🏸' },
  GOLF: { label: '골프', emoji: '⛳' },
  RUNNING: { label: '러닝', emoji: '🏃' },
  CYCLING: { label: '자전거', emoji: '🚴' },
  SWIMMING: { label: '수영', emoji: '🏊' },
  HIKING: { label: '등산', emoji: '⛰️' },
  YOGA: { label: '요가', emoji: '🧘' },
  ETC: { label: '기타', emoji: '🏅' },
};

/** 종목 코드를 이모지·한글 라벨로 매핑한다. */
export function getSportCategoryDisplay(category: SportCategory): SportCategoryDisplay {
  return SPORT_CATEGORY_DISPLAY_MAP[category];
}

export interface SportCategoryOption {
  value: SportCategory;
  label: string;
}

const SPORT_CATEGORY_VALUES = Object.keys(SPORT_CATEGORY_DISPLAY_MAP) as SportCategory[];

/** 개설 폼 종목 선택지 — BE SportCategory 12개 값 순서를 그대로 노출한다. */
export const SPORT_CATEGORY_OPTIONS: SportCategoryOption[] = SPORT_CATEGORY_VALUES.map((value) => {
  const display = SPORT_CATEGORY_DISPLAY_MAP[value];
  return { value, label: `${display.emoji} ${display.label}` };
});

/**
 * 공개여부 라벨. 비공개 커뮤니티는 가입 시 항상 승인이 필요하므로(FR-2)
 * 목록 카드에 그 성격을 함께 표기한다 (design-fe-app.md S3 와이어프레임 "비공개 승인제").
 */
export function getVisibilityLabel(visibility: CommunityVisibility): string {
  return visibility === 'PUBLIC' ? '공개' : '비공개 승인제';
}
