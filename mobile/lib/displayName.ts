/**
 * displayName.ts — 사용자 표시 이름 해석.
 *
 * BE 는 닉네임 미설정 계정에도 중립 기본값을 내려주지만, 구 응답(닉네임 필드 없음)이나 부분 응답이
 * 섞일 수 있으므로 화면 직전에서 한 번 더 폴백한다. 여기가 유일한 폴백 지점이다 —
 * 화면마다 `사용자 ${userId}` 같은 표기를 만들지 않는다(갤러리 결함 재발 방지).
 */
export const UNSET_NICKNAME_DISPLAY_NAME = '닉네임 미설정';

export function resolveDisplayName(displayName: string | null | undefined): string {
  const trimmed = displayName?.trim();
  return trimmed !== undefined && trimmed.length > 0 ? trimmed : UNSET_NICKNAME_DISPLAY_NAME;
}
