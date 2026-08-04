/**
 * 피처 플래그 종류 — 값 목록·한글 라벨의 단일 출처.
 *
 * 정답은 BE `domain/featureflag/entity/FeatureFlagType.kt` 다. 목록(S1)·생성(S2)·상세(S3) 3개
 * 화면이 각자 인라인으로 라벨을 두면 한 화면만 갱신되고 나머지가 영문 원문으로 남는다
 * (02-피처플래그-목록 결함: 종류 열 배지가 RELEASE/OPERATIONAL/EXPERIMENT 그대로 노출).
 *
 * 계약 밖 값(BE 추가·FE 미동기화)이 와도 원문을 그대로 보여준다 — 라벨을 못 찾는다고
 * 화면 전체가 죽으면 안 된다(02-파트너포털 결제 상태 전량 파싱 실패 사고와 동일 실패 모드).
 */

export const FEATURE_FLAG_TYPE_VALUES = [
  "RELEASE",
  "OPERATIONAL",
  "EXPERIMENT",
  "ENTITLEMENT",
] as const;

export type FeatureFlagTypeValue = (typeof FEATURE_FLAG_TYPE_VALUES)[number];

/** 값이 비어 있을 때 표에 채우는 자리표시자. */
const EMPTY_PLACEHOLDER = "-";

export const FEATURE_FLAG_TYPE_LABELS: Record<FeatureFlagTypeValue, string> = {
  RELEASE: "릴리즈",
  OPERATIONAL: "운영",
  EXPERIMENT: "실험",
  ENTITLEMENT: "권한",
};

function isKnownFeatureFlagType(value: string): value is FeatureFlagTypeValue {
  return (FEATURE_FLAG_TYPE_VALUES as readonly string[]).includes(value);
}

/**
 * 플래그 종류를 화면 문구로 바꾼다.
 * 계약이 어긋나 모르는 값이 와도 예외를 던지지 않고 원문을 그대로 돌려준다.
 */
export function featureFlagTypeLabel(type: string | null | undefined): string {
  if (type === null || type === undefined || type.length === 0) {
    return EMPTY_PLACEHOLDER;
  }
  return isKnownFeatureFlagType(type) ? FEATURE_FLAG_TYPE_LABELS[type] : type;
}
