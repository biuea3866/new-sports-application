/**
 * lib/feature-flags.ts — 채팅 기능 플래그 단일 게이트 검증
 * 근거: 티켓 "앱 와이어업·기능 플래그·전역 배지 통합", design-fe-app.md "기능 플래그·점진 공개".
 */
import { isFeatureEnabled } from '../feature-flags';

const ENV_KEYS = [
  'EXPO_PUBLIC_CHAT_REALTIME_ENABLED',
  'EXPO_PUBLIC_CHAT_COMMUNITY_ENABLED',
  'EXPO_PUBLIC_CHAT_GOODS_ENABLED',
] as const;

function clearFlagEnv() {
  for (const key of ENV_KEYS) {
    delete process.env[key];
  }
}

describe('isFeatureEnabled', () => {
  afterEach(() => {
    clearFlagEnv();
  });

  it('chat.goods.enabled는 환경변수 미설정 시 기본 OFF다', () => {
    clearFlagEnv();

    expect(isFeatureEnabled('chat.goods.enabled')).toBe(false);
  });

  it("chat.goods.enabled는 환경변수가 'true'일 때만 ON이다", () => {
    process.env.EXPO_PUBLIC_CHAT_GOODS_ENABLED = 'true';

    expect(isFeatureEnabled('chat.goods.enabled')).toBe(true);
  });

  it('chat.realtime.enabled는 환경변수 미설정 시 정책 기본값(ON)을 반환한다', () => {
    clearFlagEnv();

    expect(isFeatureEnabled('chat.realtime.enabled')).toBe(true);
  });

  it("chat.realtime.enabled는 환경변수가 'false'면 OFF다 — REST 폴백 대상", () => {
    process.env.EXPO_PUBLIC_CHAT_REALTIME_ENABLED = 'false';

    expect(isFeatureEnabled('chat.realtime.enabled')).toBe(false);
  });

  it('chat.community.enabled는 환경변수 미설정 시 정책 기본값(ON)을 반환한다', () => {
    clearFlagEnv();

    expect(isFeatureEnabled('chat.community.enabled')).toBe(true);
  });

  it("chat.community.enabled는 환경변수가 'false'면 OFF다", () => {
    process.env.EXPO_PUBLIC_CHAT_COMMUNITY_ENABLED = 'false';

    expect(isFeatureEnabled('chat.community.enabled')).toBe(false);
  });

  it("값 파싱은 문자열 'true'만 true로 인정한다 (예: '1'은 false)", () => {
    process.env.EXPO_PUBLIC_CHAT_GOODS_ENABLED = '1';

    expect(isFeatureEnabled('chat.goods.enabled')).toBe(false);
  });
});
