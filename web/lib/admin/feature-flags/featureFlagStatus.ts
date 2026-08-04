/**
 * 피처 플래그 상태 — 값 목록·한글 라벨의 단일 출처.
 *
 * 정답은 BE `domain/featureflag/entity/FeatureFlagStatus.kt` 다. ARCHIVED는 화면 곳곳의 액션
 * 문구("아카이브"·"재활성")와 같은 어휘를 써야 같은 화면에서 표기가 갈리지 않는다.
 *
 * 계약 밖 값이 와도 원문을 그대로 보여준다 — 02-피처플래그-목록/04-상세 결함(상태 배지가
 * ACTIVE 그대로 노출)의 재발을 막는다.
 */

export const FEATURE_FLAG_STATUS_VALUES = ["ACTIVE", "ARCHIVED"] as const;

export type FeatureFlagStatusValue = (typeof FEATURE_FLAG_STATUS_VALUES)[number];

const EMPTY_PLACEHOLDER = "-";

export const FEATURE_FLAG_STATUS_LABELS: Record<FeatureFlagStatusValue, string> = {
  ACTIVE: "활성",
  ARCHIVED: "아카이브됨",
};

/**
 * 상태 값이 계약 목록 안에 있는지 판별하는 타입 가드. 라벨 함수뿐 아니라 배지 색 맵(StatusBadge)도
 * 이 가드로 좁혀야 색 맵의 키가 `FeatureFlagStatusValue`로 제한되고, BE가 상태를 추가했는데 색
 * 맵 갱신을 빠뜨리면 컴파일 타임에 드러난다 — `Record<string, string>`로 두면 이 보호가 없다.
 */
export function isKnownFeatureFlagStatus(value: string): value is FeatureFlagStatusValue {
  return (FEATURE_FLAG_STATUS_VALUES as readonly string[]).includes(value);
}

/**
 * 플래그 상태를 화면 문구로 바꾼다.
 * 계약이 어긋나 모르는 값이 와도 예외를 던지지 않고 원문을 그대로 돌려준다.
 */
export function featureFlagStatusLabel(status: string | null | undefined): string {
  if (status === null || status === undefined || status.length === 0) {
    return EMPTY_PLACEHOLDER;
  }
  return isKnownFeatureFlagStatus(status) ? FEATURE_FLAG_STATUS_LABELS[status] : status;
}
