/**
 * 웹 지도(Leaflet) 컨트롤의 다크 모드 스타일.
 *
 * Leaflet 기본 컨트롤(줌 +/−·attribution 바)은 leaflet.css 가 흰 배경·검은 글씨로 고정한다.
 * 타일만 다크로 바꾸면 이 컨트롤들이 다크 화면에서 가장 밝은 요소로 남는다
 * (01-모바일앱/04-시설-탭.dark 캡쳐).
 *
 * leaflet.css 는 전역 스타일시트라 RN StyleSheet 로 덮을 수 없어 CSS 규칙을 주입해야 한다.
 * 주입(=DOM 접근)은 컴포넌트에 두고, **무엇을 주입하는지**는 여기 순수 함수로 분리해
 * DOM 없는 환경에서도 규칙 자체를 검증할 수 있게 한다.
 */

/** 주입한 style 태그를 다시 찾기 위한 표식 — 지도가 여러 개 떠도 한 벌만 유지한다. */
export const MAP_THEME_STYLE_ATTRIBUTE = 'data-facility-map-theme';

/** 다크일 때 지도 컨테이너에 붙는 클래스. CSS 규칙의 스코프 기준이다. */
export const DARK_MAP_CLASS_NAME = 'facility-map-dark';

/**
 * 지도 컨테이너에 붙일 클래스. 다크일 때만 스코프 클래스를 붙여 라이트 화면에는 규칙이 새지 않는다.
 */
export function mapContainerClassName(scheme: 'light' | 'dark'): string | undefined {
  return scheme === 'dark' ? DARK_MAP_CLASS_NAME : undefined;
}

/**
 * 다크 모드 컨트롤 규칙을 만든다. 색은 호출부가 테마 토큰에서 넘긴다 — 여기서 하드코딩하지 않는다.
 */
export function buildDarkControlCss(
  surfaceColor: string,
  textColor: string,
  borderColor: string
): string {
  return `
.${DARK_MAP_CLASS_NAME} .leaflet-control-zoom a {
  background-color: ${surfaceColor};
  color: ${textColor};
  border-bottom-color: ${borderColor};
}
.${DARK_MAP_CLASS_NAME} .leaflet-control-zoom a:hover {
  background-color: ${borderColor};
  color: ${textColor};
}
.${DARK_MAP_CLASS_NAME} .leaflet-control-zoom {
  border-color: ${borderColor};
}
.${DARK_MAP_CLASS_NAME} .leaflet-control-attribution {
  background-color: ${surfaceColor};
  color: ${textColor};
}
.${DARK_MAP_CLASS_NAME} .leaflet-control-attribution a {
  color: ${textColor};
}
`;
}
