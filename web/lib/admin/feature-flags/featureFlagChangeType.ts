/**
 * 감사 로그 변경 유형 — 값 목록·한글 라벨의 단일 출처.
 *
 * 정답은 BE `domain/featureflag/entity/FeatureFlagChangeType.kt` 다. ARCHIVED/ACTIVATED는
 * 상태 라벨·액션 버튼 문구("아카이브"·"재활성")와 어휘를 맞춘다.
 *
 * 계약 밖 값이 와도 원문을 그대로 보여준다 — 05-피처플래그-감사로그 결함(변경 열 배지가
 * CREATED/UPDATED 그대로 노출)의 재발을 막는다.
 */

export const FEATURE_FLAG_CHANGE_TYPE_VALUES = [
  "CREATED",
  "UPDATED",
  "ARCHIVED",
  "ACTIVATED",
] as const;

export type FeatureFlagChangeTypeValue = (typeof FEATURE_FLAG_CHANGE_TYPE_VALUES)[number];

const EMPTY_PLACEHOLDER = "-";

export const FEATURE_FLAG_CHANGE_TYPE_LABELS: Record<FeatureFlagChangeTypeValue, string> = {
  CREATED: "생성",
  UPDATED: "수정",
  ARCHIVED: "아카이브",
  ACTIVATED: "재활성",
};

function isKnownFeatureFlagChangeType(value: string): value is FeatureFlagChangeTypeValue {
  return (FEATURE_FLAG_CHANGE_TYPE_VALUES as readonly string[]).includes(value);
}

/**
 * 변경 유형을 화면 문구로 바꾼다.
 * 계약이 어긋나 모르는 값이 와도 예외를 던지지 않고 원문을 그대로 돌려준다.
 */
export function featureFlagChangeTypeLabel(changeType: string | null | undefined): string {
  if (changeType === null || changeType === undefined || changeType.length === 0) {
    return EMPTY_PLACEHOLDER;
  }
  return isKnownFeatureFlagChangeType(changeType)
    ? FEATURE_FLAG_CHANGE_TYPE_LABELS[changeType]
    : changeType;
}
