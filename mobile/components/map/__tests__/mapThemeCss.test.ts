/**
 * 웹 지도 컨트롤 다크 스타일 규칙 계약.
 *
 * 회귀 방지: 타일은 다크로 바뀌었는데 Leaflet 기본 컨트롤(줌 +/−·attribution 바)은 leaflet.css
 * 기본값인 흰 배경 그대로라, 다크 화면에서 가장 밝은 요소로 남았다
 * (01-모바일앱/04-시설-탭.dark 캡쳐).
 *
 * 실제 주입은 DOM 작업이라 RN 테스트 환경에서 실행되지 않는다. **무엇을 주입하는지**(어떤
 * 셀렉터를 어떤 색으로 덮는지)를 여기서 고정한다.
 */
import {
  DARK_MAP_CLASS_NAME,
  MAP_THEME_STYLE_ATTRIBUTE,
  buildDarkControlCss,
  mapContainerClassName,
} from '../mapThemeCss';
import { darkTokens, lightTokens } from '../../../theme/tokens';

describe('지도 컨트롤 다크 스타일', () => {
  const css = buildDarkControlCss(darkTokens.surface, darkTokens.textPrimary, darkTokens.border);

  it('줌 컨트롤 배경·글자색을 덮는다', () => {
    expect(css).toContain(`.${DARK_MAP_CLASS_NAME} .leaflet-control-zoom a`);
    expect(css).toContain(`background-color: ${darkTokens.surface}`);
    expect(css).toContain(`color: ${darkTokens.textPrimary}`);
  });

  it('attribution 바 배경·글자색을 덮는다', () => {
    expect(css).toContain(`.${DARK_MAP_CLASS_NAME} .leaflet-control-attribution`);
    expect(css).toContain(`.${DARK_MAP_CLASS_NAME} .leaflet-control-attribution a`);
  });

  it('모든 규칙이 다크 클래스로 한정된다 — 라이트 화면에 새지 않는다', () => {
    const selectors = css
      .split('\n')
      .filter((line) => line.trim().endsWith('{'))
      .map((line) => line.trim());

    expect(selectors.length).toBeGreaterThan(0);
    for (const selector of selectors) {
      expect(selector.startsWith(`.${DARK_MAP_CLASS_NAME} `)).toBe(true);
    }
  });

  // 색은 테마 토큰에서만 온다 — 규칙 안에 색을 박아 두면 토큰이 바뀌어도 지도만 옛 색으로 남는다.
  it('라이트 토큰을 넘기면 그 색이 그대로 쓰인다', () => {
    const lightCss = buildDarkControlCss(
      lightTokens.surface,
      lightTokens.textPrimary,
      lightTokens.border
    );

    expect(lightCss).toContain(lightTokens.surface);
    expect(lightCss).not.toContain(darkTokens.surface);
  });

  it('주입한 style 태그를 다시 찾을 표식이 있다', () => {
    expect(MAP_THEME_STYLE_ATTRIBUTE.length).toBeGreaterThan(0);
  });
});

describe('지도 컨테이너 클래스', () => {
  it('다크 모드에서는 스코프 클래스를 붙인다', () => {
    expect(mapContainerClassName('dark')).toBe(DARK_MAP_CLASS_NAME);
  });

  it('라이트 모드에서는 클래스를 붙이지 않는다 — 규칙이 라이트 화면에 새지 않는다', () => {
    expect(mapContainerClassName('light')).toBeUndefined();
  });
});
