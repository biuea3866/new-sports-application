/**
 * lib/feature-flags.ts — 채팅 기능 플래그 단일 게이트 검증
 * 근거: 티켓 "앱 와이어업·기능 플래그·전역 배지 통합", design-fe-app.md "기능 플래그·점진 공개".
 */
import { isFeatureEnabled } from '../feature-flags';

const ENV_KEYS = [
  'EXPO_PUBLIC_CHAT_REALTIME_ENABLED',
  'EXPO_PUBLIC_CHAT_COMMUNITY_ENABLED',
  'EXPO_PUBLIC_CHAT_GOODS_ENABLED',
  'EXPO_PUBLIC_RECRUITMENT_ENABLED',
  'EXPO_PUBLIC_FACILITY_PROGRAM_ENABLED',
  'EXPO_PUBLIC_COMMUNITY_POST_ENABLED',
  'EXPO_PUBLIC_COMMUNITY_BOOKING_ENABLED',
  'EXPO_PUBLIC_CATALOG_ENABLED',
  'EXPO_PUBLIC_ORDERS_UNIFIED_ENABLED',
  'EXPO_PUBLIC_VIRTUAL_QUEUE_ENABLED',
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

describe('isFeatureEnabled — 모집·시설상품·모임게시판·소모임예약 플래그 (design-fe-app "기능 플래그·점진 공개")', () => {
  afterEach(() => {
    clearFlagEnv();
  });

  it.each([
    ['recruitment.enabled', 'EXPO_PUBLIC_RECRUITMENT_ENABLED'],
    ['facility.program.enabled', 'EXPO_PUBLIC_FACILITY_PROGRAM_ENABLED'],
    ['community.post.enabled', 'EXPO_PUBLIC_COMMUNITY_POST_ENABLED'],
    ['community.booking.enabled', 'EXPO_PUBLIC_COMMUNITY_BOOKING_ENABLED'],
  ] as const)('%s는 환경변수 미설정 시 기본 OFF다(점진 공개 전제)', (flag, _envKey) => {
    clearFlagEnv();

    expect(isFeatureEnabled(flag)).toBe(false);
  });

  it("recruitment.enabled는 환경변수가 'true'일 때만 ON이다", () => {
    process.env.EXPO_PUBLIC_RECRUITMENT_ENABLED = 'true';

    expect(isFeatureEnabled('recruitment.enabled')).toBe(true);
  });

  it("facility.program.enabled는 'true' 이외 값이면 OFF다", () => {
    process.env.EXPO_PUBLIC_FACILITY_PROGRAM_ENABLED = 'yes';

    expect(isFeatureEnabled('facility.program.enabled')).toBe(false);
  });

  it("community.post.enabled는 환경변수가 'true'일 때만 ON이다", () => {
    process.env.EXPO_PUBLIC_COMMUNITY_POST_ENABLED = 'true';

    expect(isFeatureEnabled('community.post.enabled')).toBe(true);
  });

  it("community.booking.enabled는 환경변수가 'true'일 때만 ON이다", () => {
    process.env.EXPO_PUBLIC_COMMUNITY_BOOKING_ENABLED = 'true';

    expect(isFeatureEnabled('community.booking.enabled')).toBe(true);
  });
});

describe('isFeatureEnabled — 통합 검색·통합 주문 내역 진입점 플래그', () => {
  afterEach(() => {
    clearFlagEnv();
  });

  it.each([
    ['catalog.enabled', 'EXPO_PUBLIC_CATALOG_ENABLED'],
    ['orders.unified.enabled', 'EXPO_PUBLIC_ORDERS_UNIFIED_ENABLED'],
  ] as const)('%s는 환경변수 미설정 시 기본 OFF다(BE 파사드 API 준비 전 숨김)', (flag, _envKey) => {
    clearFlagEnv();

    expect(isFeatureEnabled(flag)).toBe(false);
  });

  it("catalog.enabled는 환경변수가 'true'일 때만 ON이다", () => {
    process.env.EXPO_PUBLIC_CATALOG_ENABLED = 'true';

    expect(isFeatureEnabled('catalog.enabled')).toBe(true);
  });

  it("catalog.enabled는 'true' 이외 값이면 OFF다", () => {
    process.env.EXPO_PUBLIC_CATALOG_ENABLED = 'yes';

    expect(isFeatureEnabled('catalog.enabled')).toBe(false);
  });

  it("orders.unified.enabled는 환경변수가 'true'일 때만 ON이다", () => {
    process.env.EXPO_PUBLIC_ORDERS_UNIFIED_ENABLED = 'true';

    expect(isFeatureEnabled('orders.unified.enabled')).toBe(true);
  });
});

describe('isFeatureEnabled — 가상 대기열 진입 플래그 (FE-02, design-fe-app.md "Release Scenario")', () => {
  afterEach(() => {
    clearFlagEnv();
  });

  it('virtual-queue.enabled는 환경변수 미설정 시 기본 OFF다(BE 플래그와 lockstep 전제)', () => {
    clearFlagEnv();

    expect(isFeatureEnabled('virtual-queue.enabled')).toBe(false);
  });

  it("virtual-queue.enabled는 환경변수가 'true'일 때만 ON이다", () => {
    process.env.EXPO_PUBLIC_VIRTUAL_QUEUE_ENABLED = 'true';

    expect(isFeatureEnabled('virtual-queue.enabled')).toBe(true);
  });

  it("virtual-queue.enabled는 'true' 이외 값이면 OFF다", () => {
    process.env.EXPO_PUBLIC_VIRTUAL_QUEUE_ENABLED = 'yes';

    expect(isFeatureEnabled('virtual-queue.enabled')).toBe(false);
  });
});
