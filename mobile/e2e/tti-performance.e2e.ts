/**
 * E2E TTI(Time To Interactive) 성능 시나리오
 *
 * S-03: 콜드 스타트 → 홈 탭 렌더 TTI가 P95 2초 이하다
 *
 * [SKIP 이유]
 * - Detox + react-native-performance 조합은 실제 디바이스/시뮬레이터가 필요합니다.
 * - CI 환경에서는 네이티브 빌드 없이 실행 불가합니다.
 * - 성능 측정은 로컬 mid-range Android 기기에서 `pnpm detox:test:android`로 수동 검증합니다.
 * - 목표: 콜드 스타트 → 홈 탭 첫 렌더 TTI P95 ≤ 2000ms
 */

// eslint-disable-next-line @typescript-eslint/no-var-requires
const { device, element, by, expect: detoxExpect } = require('detox');

describe.skip('S-03 TTI 성능 측정 (Detox — CI skip)', () => {
  const TTI_P95_TARGET_MS = 2000;
  const SAMPLE_COUNT = 10;

  it.todo('[S-03] 콜드 스타트 → 홈 탭 TTI P95 2초 이하 — react-native-performance 연동 필요');

  /**
   * 아래는 react-native-performance 라이브러리 연동 후 활성화할 구현 골격입니다.
   * 현재는 라이브러리 미설치로 인해 todo로 남깁니다.
   *
   * 구현 방향:
   * 1. `npm install react-native-performance` 후 네이티브 링크
   * 2. HomeScreen에서 `performance.mark('home-tti')` 마킹
   * 3. Detox에서 `device.getPerfStats()` 또는 커스텀 브릿지로 TTI 수집
   * 4. SAMPLE_COUNT번 반복 → P95 계산 → TTI_P95_TARGET_MS 이하 assert
   */
  it(`[S-03 골격] ${SAMPLE_COUNT}회 반복 콜드 스타트 TTI P95 ≤ ${TTI_P95_TARGET_MS}ms`, async () => {
    const measurements: number[] = [];

    for (let i = 0; i < SAMPLE_COUNT; i++) {
      const startTime = Date.now();
      await device.launchApp({ newInstance: true });
      await detoxExpect(element(by.id('home-screen'))).toBeVisible();
      measurements.push(Date.now() - startTime);
      await device.terminateApp();
    }

    measurements.sort((a, b) => a - b);
    const p95Index = Math.ceil(measurements.length * 0.95) - 1;
    const p95 = measurements[p95Index];

    expect(p95).toBeLessThanOrEqual(TTI_P95_TARGET_MS);
  });
});
