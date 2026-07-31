/**
 * FacilityMap(웹) — 서버 렌더(SSR) 안전성 검증.
 *
 * Expo Router 웹은 페이지를 node에서 먼저 렌더한다(window 없음). Leaflet은 임포트 시점에
 * window를 만지므로 모듈 최상위에서 불러오면 "window is not defined"로 앱 전체가 500이 된다.
 * 지도 라이브러리는 이펙트(클라이언트) 안에서만 로드해야 한다.
 */
import { readFileSync } from 'fs';
import { join } from 'path';

const SOURCE_PATH = join(__dirname, '..', 'FacilityMap.web.tsx');

describe('FacilityMap.web — SSR 안전성', () => {
  const source = readFileSync(SOURCE_PATH, 'utf-8');

  it('leaflet을 모듈 최상위에서 값(런타임) import하지 않는다', () => {
    // `import type`은 컴파일 시 지워지므로 서버 렌더에 영향을 주지 않는다 — 값 import만 막는다.
    const topLevelValueImport = /^import\s+(?!type\s)[^;]*from\s+'leaflet'/m;
    expect(source).not.toMatch(topLevelValueImport);
  });

  it('leaflet 스타일도 모듈 최상위에서 정적 import하지 않는다', () => {
    expect(source).not.toMatch(/^import\s+'leaflet\/dist\/leaflet\.css'/m);
  });

  it('타입 전용 import는 허용한다(런타임 코드가 남지 않는다)', () => {
    const hasTypeOnlyImportOrInlineType =
      /import\s+type\s+/.test(source) || /typeof import\('leaflet'\)/.test(source);
    expect(hasTypeOnlyImportOrInlineType).toBe(true);
  });

  it('지도 라이브러리를 이펙트 안에서 지연 로드한다', () => {
    expect(source).toMatch(/require\('leaflet'\)|await import\('leaflet'\)/);
  });
});
