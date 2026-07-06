/**
 * lib/feature-flags.ts — 채팅 기능 플래그 단일 게이트
 *
 * 근거: 티켓 "앱 와이어업·기능 플래그·전역 배지 통합", design-fe-app.md
 * "기능 플래그·점진 공개". 값 소스는 `EXPO_PUBLIC_CHAT_*_ENABLED` 환경변수다.
 *
 * 파싱 규칙:
 * - 환경변수가 설정되어 있으면 문자열 `'true'`만 true로 인정한다(그 외 값은 false).
 * - 환경변수가 아예 미설정(undefined)이면 아래 플래그별 정책 기본값을 사용한다.
 *
 * 정책 기본값 (BE Release Scenario Phase 정합):
 * - chat.realtime.enabled: 기본 true — BE-06 STOMP 브로커·FE-06 소켓 훅 머지 완료(Phase 2)
 * - chat.community.enabled: 기본 true — BE-08/09 커뮤니티·초대 API 머지 완료(Phase 2)
 * - chat.goods.enabled: 기본 false — BE-11 굿즈 채팅 API 미완료(Phase 3/2단계)
 *
 * 이 플래그를 실제로 소비하는 화면/훅(`useChatSocket`·`rooms/index`·`invite/[roomId]`·
 * `product/[id]/index` 등)은 각 소유 티켓이 이 함수를 호출해 게이팅한다 — FE-15는
 * 값 정의만 소유한다.
 */

export type ChatFeatureFlag =
  | 'chat.realtime.enabled'
  | 'chat.community.enabled'
  | 'chat.goods.enabled';

interface FeatureFlagDefinition {
  envKey: string;
  defaultWhenUnset: boolean;
}

const FEATURE_FLAG_DEFINITIONS: Record<ChatFeatureFlag, FeatureFlagDefinition> = {
  'chat.realtime.enabled': {
    envKey: 'EXPO_PUBLIC_CHAT_REALTIME_ENABLED',
    defaultWhenUnset: true,
  },
  'chat.community.enabled': {
    envKey: 'EXPO_PUBLIC_CHAT_COMMUNITY_ENABLED',
    defaultWhenUnset: true,
  },
  'chat.goods.enabled': {
    envKey: 'EXPO_PUBLIC_CHAT_GOODS_ENABLED',
    defaultWhenUnset: false,
  },
};

/** 지정한 채팅 기능 플래그가 활성화되어 있는지 반환합니다. */
export function isFeatureEnabled(flag: ChatFeatureFlag): boolean {
  const definition = FEATURE_FLAG_DEFINITIONS[flag];
  const rawValue = process.env[definition.envKey];

  if (rawValue === undefined) {
    return definition.defaultWhenUnset;
  }

  return rawValue === 'true';
}
