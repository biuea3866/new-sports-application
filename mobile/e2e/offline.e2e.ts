/**
 * E2E 오프라인 시나리오 — Detox 필요
 *
 * S-01: 오프라인 진입 시 시설 목록 화면이 마지막 캐시 응답으로 즉시 표시된다
 * S-02: 오프라인 결제 차단 — 결제 버튼 탭 시 토스트 + 차단되어 BE 호출이 발생하지 않는다
 *
 * [SKIP 이유]
 * Detox는 실제 iOS 시뮬레이터/Android 에뮬레이터 + 앱 빌드가 필요합니다.
 * CI 환경에서는 native 빌드 및 시뮬레이터가 없으므로 describe.skip으로 건너뜁니다.
 * 로컬 디바이스가 있는 환경에서는 `pnpm detox:test:ios`로 실행하세요.
 */

// eslint-disable-next-line @typescript-eslint/no-var-requires
const { device, element, by, expect: detoxExpect } = require('detox');

describe.skip('S-01 / S-02 오프라인 시나리오 (Detox — CI skip)', () => {
  beforeAll(async () => {
    await device.launchApp({ newInstance: true });
  });

  afterAll(async () => {
    await device.terminateApp();
  });

  it('[S-01] 오프라인 진입 시 시설 목록이 캐시된 데이터로 표시된다', async () => {
    // 1. 앱을 온라인 상태에서 실행해 시설 목록 캐시
    await device.setURLBlacklist([]);
    await element(by.id('tab-home')).tap();
    await detoxExpect(element(by.id('facility-list'))).toBeVisible();

    // 2. 네트워크 차단
    await device.setURLBlacklist(['.*']);

    // 3. 앱을 백그라운드→포어그라운드 전환
    await device.sendToHome();
    await device.launchApp({ newInstance: false });

    // 4. 캐시된 시설 목록이 즉시 노출되어야 함
    await detoxExpect(element(by.id('facility-list'))).toBeVisible();
    await detoxExpect(element(by.text('네트워크 연결이 없습니다'))).toBeVisible();
  });

  it('[S-02] 오프라인 상태에서 결제 버튼을 탭하면 차단 메시지가 표시된다', async () => {
    // 네트워크 차단 상태 유지
    await device.setURLBlacklist(['.*']);

    await element(by.id('tab-home')).tap();
    await element(by.id('facility-item-0')).tap();
    await element(by.id('payment-button')).tap();

    // Alert 표시 확인
    await detoxExpect(element(by.text('네트워크 연결 필요'))).toBeVisible();

    // 네트워크 복구
    await device.setURLBlacklist([]);
  });
});
