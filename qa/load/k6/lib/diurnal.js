// qa/load/k6/lib/diurnal.js
// INFRA-01: 일주기(diurnal) 배율표 → k6 ramping-arrival-rate stage 확장기.
//
// 근거: TDD "인터페이스·계약" 곡선 배율표(00–05=0.05 ~ 11:30–13:00 점심 피크 1.00 ~
// 18–20 저녁 피크 0.95→1.00 ~ 22–23=0.20→0.08). B2C read:write = 70:30(peak 2100:900),
// B2B peak=100. TIME_SCALE(env)로 24h↔압축 스케일 조정은 호출부 스크립트가 담당하고,
// 이 모듈의 함수는 timeScale 파라미터만 받는다(순수 함수, 단위테스트 용이).

// 24시간 배율 곡선의 keyframe(시각, peak 대비 배율) 목록.
// 연속한 keyframe 사이는 선형 램프로 연결된다 — 동일 배율이 이어지는 구간은 flat(hold),
// 짧은 구간(예: 13:00→13:06)은 사실상 즉시 전환(jump)을 표현한다.
// 시각(hour)은 0~24 범위 소수(예: 11.5 = 11:30)이며 합계 24h를 채운다.
export const DIURNAL_CURVE = [
  { hour: 0, multiplier: 0.05 }, // 00시 — 심야 시작
  { hour: 5, multiplier: 0.05 }, // 00–05 flat(심야)
  { hour: 6, multiplier: 0.1 }, // 06–08 램프 시작
  { hour: 8, multiplier: 0.3 }, // 06–08 램프 종료
  { hour: 9, multiplier: 0.45 }, // 09–11 램프 시작
  { hour: 11, multiplier: 0.7 }, // 09–11 램프 종료
  { hour: 11.5, multiplier: 1.0 }, // 11:30 점심 피크 도달
  { hour: 13, multiplier: 1.0 }, // 13:00까지 점심 피크 유지
  { hour: 13.1, multiplier: 0.55 }, // 13:00 직후 오후 평시로 급락(6분 전환)
  { hour: 17, multiplier: 0.55 }, // 13–17 flat
  { hour: 18, multiplier: 0.95 }, // 18–20 저녁 램프 시작
  { hour: 20, multiplier: 1.0 }, // 20시 저녁 피크 도달
  { hour: 21, multiplier: 0.5 }, // 21시 하락
  { hour: 22, multiplier: 0.2 }, // 22–23 램프 시작
  { hour: 23, multiplier: 0.08 }, // 22–23 램프 종료(심야 저점)
  { hour: 24, multiplier: 0.05 }, // 자정 복귀(다음 00–05 심야와 연결)
];

const SECONDS_PER_HOUR = 3600;

/**
 * 배율표(curve)를 k6 ramping-arrival-rate `stages[{target, duration}]`로 확장한다.
 * 목표TPS(t) = round(peakRate × 배율(t)). timeScale=1이면 실시간 24h, timeScale=0.2면
 * 압축된 24h(기존 1/5 관례)로 전체 stage duration 합이 비례 축소된다.
 *
 * @param {number} peakRate 곡선의 피크(배율 1.00) 시점 목표 TPS
 * @param {Array<{hour:number, multiplier:number}>} curve keyframe 목록(기본 DIURNAL_CURVE)
 * @param {{timeScale?: number}} options timeScale 기본값 1(24h 실시간)
 * @returns {Array<{target:number, duration:string}>}
 */
export function buildStages(peakRate, curve = DIURNAL_CURVE, { timeScale = 1 } = {}) {
  const stages = [];
  for (let i = 1; i < curve.length; i++) {
    const previous = curve[i - 1];
    const current = curve[i];
    const durationHours = current.hour - previous.hour;
    const durationSeconds = Math.round(durationHours * SECONDS_PER_HOUR * timeScale);
    stages.push({
      target: Math.round(peakRate * current.multiplier),
      duration: `${durationSeconds}s`,
    });
  }
  return stages;
}

/** B2C 조회(read) 곡선 — peak 2100 (= B2C 목표 3000 × 0.7). */
export function b2cReadStages(timeScale = 1) {
  return buildStages(2100, DIURNAL_CURVE, { timeScale });
}

/** B2C 쓰기(write) 곡선 — peak 900 (= B2C 목표 3000 × 0.3). */
export function b2cWriteStages(timeScale = 1) {
  return buildStages(900, DIURNAL_CURVE, { timeScale });
}

/** B2B 곡선 — peak 100 (파트너 상품/이벤트 등록, 1000건/일 목표). */
export function b2bStages(timeScale = 1) {
  return buildStages(100, DIURNAL_CURVE, { timeScale });
}

/**
 * 마케팅 스파이크 곡선 — 0 → 20000을 30초 내 도달 후 유지, 감쇠.
 * timeScale을 받지 않는다(항상 실시간 초 단위 급경사 재현이 목적).
 */
export function spikeStages() {
  return [
    { target: 20000, duration: "30s" }, // 0 → 20000 급증(30초 내 도전)
    { target: 20000, duration: "2m" }, // steady 유지
    { target: 0, duration: "1m" }, // ramp-down 감쇠
  ];
}
