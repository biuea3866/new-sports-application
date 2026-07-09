/**
 * lib/feature-flags.ts — 앱 기능 플래그 단일 게이트
 *
 * 근거: 티켓 "앱 와이어업·기능 플래그·전역 배지 통합", `20260704-채팅시스템고도화-design-fe-app.md`
 * "기능 플래그·점진 공개", `20260707-모집-시설상품-소모임예약-게시글연동-design-fe-app.md`
 * "Release Scenario — 기능 플래그·점진 공개". 값 소스는 `EXPO_PUBLIC_*_ENABLED` 환경변수다.
 *
 * 파싱 규칙:
 * - 환경변수가 설정되어 있으면 문자열 `'true'`만 true로 인정한다(그 외 값은 false).
 * - 환경변수가 아예 미설정(undefined)이면 아래 플래그별 정책 기본값을 사용한다.
 *
 * 정책 기본값 (BE Release Scenario Phase 정합):
 * - chat.realtime.enabled: 기본 true — BE-06 STOMP 브로커·FE-06 소켓 훅 머지 완료(Phase 2)
 * - chat.community.enabled: 기본 true — BE-08/09 커뮤니티·초대 API 머지 완료(Phase 2)
 * - chat.goods.enabled: 기본 false — BE-11 굿즈 채팅 API 미완료(Phase 3/2단계)
 * - recruitment.enabled / facility.program.enabled / community.post.enabled /
 *   community.booking.enabled: 기본 false — BE `@ConditionalOnProperty`(동일 property key)
 *   기본값(matchIfMissing=false)과 정합한 점진 공개 전제. BE 플래그 순서(community.booking →
 *   program → recruitment)와 축 단위로 맞춰 ON 전환한다.
 * - catalog.enabled / orders.unified.enabled: 기본 false — BE 파사드 API(`/api/catalog`·
 *   `/api/orders`) 배포 전에는 진입점을 숨긴다(`20260708-상품주문-공유상위컨텍스트-design-fe-app.md`
 *   "Release Scenario — 기능 플래그·점진 공개", FE-11).
 *
 * 이 플래그를 실제로 소비하는 화면/훅은 각 소유 티켓이 이 함수를 호출해 게이팅한다 — 이
 * 파일은 값 정의만 소유한다(FE-15 선례 계승).
 */

export type ChatFeatureFlag =
  | 'chat.realtime.enabled'
  | 'chat.community.enabled'
  | 'chat.goods.enabled';

/**
 * 모집·시설상품·모임게시판·소모임예약 진입 플래그 — design-fe-app "Release Scenario" 표.
 * BE `facility.program.enabled`/`community.booking.enabled` property key와 동일한
 * 값 문자열을 사용해 두 레이어의 플래그 상태를 대조하기 쉽게 한다.
 */
export type DomainFeatureFlag =
  | 'recruitment.enabled'
  | 'facility.program.enabled'
  | 'community.post.enabled'
  | 'community.booking.enabled'
  | 'catalog.enabled'
  | 'orders.unified.enabled';

export type FeatureFlag = ChatFeatureFlag | DomainFeatureFlag;

interface FeatureFlagDefinition {
  envKey: string;
  defaultWhenUnset: boolean;
}

const FEATURE_FLAG_DEFINITIONS: Record<FeatureFlag, FeatureFlagDefinition> = {
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
  'recruitment.enabled': {
    envKey: 'EXPO_PUBLIC_RECRUITMENT_ENABLED',
    defaultWhenUnset: false,
  },
  'facility.program.enabled': {
    envKey: 'EXPO_PUBLIC_FACILITY_PROGRAM_ENABLED',
    defaultWhenUnset: false,
  },
  'community.post.enabled': {
    envKey: 'EXPO_PUBLIC_COMMUNITY_POST_ENABLED',
    defaultWhenUnset: false,
  },
  'community.booking.enabled': {
    envKey: 'EXPO_PUBLIC_COMMUNITY_BOOKING_ENABLED',
    defaultWhenUnset: false,
  },
  'catalog.enabled': {
    envKey: 'EXPO_PUBLIC_CATALOG_ENABLED',
    defaultWhenUnset: false,
  },
  'orders.unified.enabled': {
    envKey: 'EXPO_PUBLIC_ORDERS_UNIFIED_ENABLED',
    defaultWhenUnset: false,
  },
};

/** 지정한 기능 플래그가 활성화되어 있는지 반환합니다. */
export function isFeatureEnabled(flag: FeatureFlag): boolean {
  const definition = FEATURE_FLAG_DEFINITIONS[flag];
  const rawValue = process.env[definition.envKey];

  if (rawValue === undefined) {
    return definition.defaultWhenUnset;
  }

  return rawValue === 'true';
}
