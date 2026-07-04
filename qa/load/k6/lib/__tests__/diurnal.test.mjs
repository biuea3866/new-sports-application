// INFRA-01 검증 — lib/diurnal.js
//
// k6 자체에는 단위 테스트 러너가 없어(하네스 없음), 순수 함수(buildStages 등)는
// Node.js 내장 테스트 러너(node:test)로 검증한다 — k6 런타임 의존(http/k6 모듈) 없이
// 로직만 테스트하므로 node로 직접 import·실행이 가능하다.
//
// 근거: 티켓 "테스트 케이스" 6개 중 diurnal.js 관련 4개.
import { test } from "node:test";
import assert from "node:assert/strict";
import {
  DIURNAL_CURVE,
  buildStages,
  b2cReadStages,
  b2cWriteStages,
  b2bStages,
  spikeStages,
} from "../diurnal.js";

function parseSeconds(duration) {
  const match = /^(\d+)s$/.exec(duration);
  assert.ok(match, `duration은 "<초>s" 형식이어야 한다: ${duration}`);
  return Number(match[1]);
}

test("점심·저녁 시각 stage의 target은 peak과 같고, 심야 stage는 약 0.05×peak이다", () => {
  const peak = 1000;
  const stages = buildStages(peak, DIURNAL_CURVE, { timeScale: 1 });

  // 심야(00–05, curve[1]) — target = peak * 0.05
  assert.equal(stages[0].target, Math.round(peak * 0.05));

  // 점심 피크 도달·유지 구간(curve[6]=11:30, curve[7]=13:00 모두 multiplier=1.0)
  const lunchStageIndexes = [5, 6]; // buildStages i=6,7 → stages[5],[6] (0-based)
  for (const index of lunchStageIndexes) {
    assert.equal(stages[index].target, peak, `stages[${index}]는 점심 피크 target=peak이어야 한다`);
  }

  // 저녁 피크 도달 구간(curve[11]=20:00, multiplier=1.0)
  assert.equal(stages[10].target, peak, "stages[10]는 저녁 피크 target=peak이어야 한다");
});

test("b2cReadStages와 b2cWriteStages 피크 합이 B2C 목표(3000)의 70:30 비율(2100:900)이다", () => {
  const readPeak = Math.max(...b2cReadStages(1).map((stage) => stage.target));
  const writePeak = Math.max(...b2cWriteStages(1).map((stage) => stage.target));

  assert.equal(readPeak, 2100);
  assert.equal(writePeak, 900);
  assert.equal(readPeak + writePeak, 3000);
});

test("timeScale=0.2면 전체 stage duration 합이 실시간의 1/5로 비례 축소되고 곡선 형상은 보존된다", () => {
  const realtimeStages = buildStages(1000, DIURNAL_CURVE, { timeScale: 1 });
  const compressedStages = buildStages(1000, DIURNAL_CURVE, { timeScale: 0.2 });

  const realtimeTotal = realtimeStages.reduce((sum, stage) => sum + parseSeconds(stage.duration), 0);
  const compressedTotal = compressedStages.reduce((sum, stage) => sum + parseSeconds(stage.duration), 0);

  // 24h = 86400s 실시간, 0.2 압축 시 17280s(1/5). 스테이지별 반올림 오차를 감안해 ±1% 허용.
  const expectedRatio = 5;
  const actualRatio = realtimeTotal / compressedTotal;
  assert.ok(
    Math.abs(actualRatio - expectedRatio) / expectedRatio < 0.01,
    `duration 합 비율이 1/5(축소)에 근접해야 한다: realtime=${realtimeTotal}s compressed=${compressedTotal}s ratio=${actualRatio}`
  );

  // 곡선 형상(피크 위치 순서) 보존 — target은 timeScale과 무관하게 동일해야 한다.
  assert.deepEqual(
    realtimeStages.map((stage) => stage.target),
    compressedStages.map((stage) => stage.target)
  );
});

test("spikeStages는 첫 stage에서 30초 이내 target 20000에 도달하는 급경사를 만든다", () => {
  const stages = spikeStages();

  assert.equal(stages[0].target, 20000);
  assert.ok(
    parseSeconds(stages[0].duration) <= 30,
    `첫 stage duration은 30초 이내여야 한다: ${stages[0].duration}`
  );
});

test("b2bStages 피크는 100이다", () => {
  const peak = Math.max(...b2bStages(1).map((stage) => stage.target));
  assert.equal(peak, 100);
});
