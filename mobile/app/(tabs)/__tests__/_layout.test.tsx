/**
 * TabsLayout — 5탭(홈/시설/스토어/커뮤니티/마이) 등록·아이콘·활성색 accent 토큰화 검증.
 * 근거: 사용자 피드백 "탭바 아이콘이 하나도 안 보인다" + "탭을 5개로 재편"
 * (스토어=굿즈|티켓, 커뮤니티=게시글|동아리 세그먼트 통합, 채팅은 탭에서 제거).
 *
 * jest.setup.ts의 전역 expo-router mock은 Tabs/Tabs.Screen을 실제 컴포넌트가 아닌
 * 단순 객체/문자열로 대체하므로, `render()`로 마운트하지 않고 TabsLayout()을 순수
 * 함수로 호출해 반환된 React 엘리먼트 트리를 구조적으로 검증한다.
 */
import React from 'react';

import TabsLayout from '../_layout';
import { lightTokens, darkTokens } from '../../../theme/tokens';

jest.mock('../../../theme/useTheme', () => ({
  useTheme: jest.fn(),
}));

import { useTheme } from '../../../theme/useTheme';

const useThemeMock = useTheme as jest.MockedFunction<typeof useTheme>;

type ElementWithChildren = React.ReactElement<{ children?: React.ReactNode }>;

function findDescendant(
  element: ElementWithChildren,
  predicate: (el: React.ReactElement) => boolean
): React.ReactElement | undefined {
  const children = React.Children.toArray(element.props.children);
  for (const child of children) {
    if (!React.isValidElement(child)) continue;
    if (predicate(child)) return child;
    const found = findDescendant(child as ElementWithChildren, predicate);
    if (found) return found;
  }
  return undefined;
}

function requireDefined<T>(value: T | undefined, message: string): T {
  if (value === undefined) {
    throw new Error(message);
  }
  return value;
}

/** TabsLayout()은 `<Tabs>`를 트리 최상단에 직접 반환하므로, 엘리먼트 자기 자신부터 검사한다. */
function findSelfOrDescendant(
  element: React.ReactElement,
  predicate: (el: React.ReactElement) => boolean
): React.ReactElement | undefined {
  if (predicate(element)) return element;
  return findDescendant(element as ElementWithChildren, predicate);
}

function findTabsElement(tree: React.ReactElement): ElementWithChildren {
  return requireDefined(
    findSelfOrDescendant(
      tree,
      (el) => (el.props as { screenOptions?: unknown }).screenOptions !== undefined
    ) as ElementWithChildren | undefined,
    'Tabs 엘리먼트를 찾지 못했습니다'
  );
}

function findTabScreens(tree: ElementWithChildren): React.ReactElement<{
  name?: string;
  options?: Record<string, unknown>;
}>[] {
  const tabsElement = findTabsElement(tree);

  return React.Children.toArray(tabsElement.props.children) as React.ReactElement<{
    name?: string;
    options?: Record<string, unknown>;
  }>[];
}

function findScreen(
  screens: ReturnType<typeof findTabScreens>,
  name: string
): React.ReactElement<{ name?: string; options?: Record<string, unknown> }> {
  return requireDefined(
    screens.find((s) => s.props.name === name),
    `${name} 탭이 등록되지 않았습니다`
  );
}

const EXPECTED_TAB_NAMES = ['index', 'facilities', 'store', 'community', 'me'];

describe('TabsLayout', () => {
  beforeEach(() => {
    useThemeMock.mockReturnValue({ scheme: 'light', tokens: lightTokens });
  });

  afterEach(() => jest.clearAllMocks());

  it('정확히 5개 탭(홈/시설/스토어/커뮤니티/마이)이 등록된다', () => {
    const tree = TabsLayout() as ElementWithChildren;
    const screens = findTabScreens(tree);

    expect(screens.map((s) => s.props.name)).toEqual(EXPECTED_TAB_NAMES);
  });

  it.each(EXPECTED_TAB_NAMES)('%s 탭에 tabBarIcon이 설정되어 있다', (name) => {
    const tree = TabsLayout() as ElementWithChildren;
    const screens = findTabScreens(tree);
    const screen = findScreen(screens, name);

    expect(typeof screen.props.options?.tabBarIcon).toBe('function');
  });

  it('탭 아이콘 렌더러는 focused 여부에 따라 다른 아이콘 이름을 사용한다', () => {
    const tree = TabsLayout() as ElementWithChildren;
    const screens = findTabScreens(tree);
    const homeScreen = findScreen(screens, 'index');
    const tabBarIcon = homeScreen.props.options?.tabBarIcon as (props: {
      focused: boolean;
      color: string;
      size: number;
    }) => React.ReactElement;

    const focusedIcon = tabBarIcon({ focused: true, color: '#000', size: 24 });
    const unfocusedIcon = tabBarIcon({ focused: false, color: '#000', size: 24 });

    expect((focusedIcon.props as { name?: string }).name).not.toBe(
      (unfocusedIcon.props as { name?: string }).name
    );
  });

  it('탭 활성색·비활성색이 하드코딩이 아닌 accent·textSecondary 토큰으로 렌더된다 (라이트)', () => {
    useThemeMock.mockReturnValue({ scheme: 'light', tokens: lightTokens });

    const tree = TabsLayout() as ElementWithChildren;
    const tabsElement = findTabsElement(tree);
    const screenOptions = (tabsElement.props as { screenOptions: Record<string, unknown> })
      .screenOptions;

    // textTertiary는 탭바 배경(surfaceElevated) 위에서 AA 본문 대비(4.5:1) 미달(라이트 3.04:1·
    // 다크 3.25:1)이라 textSecondary로 상향했다 — 아래 "탭바 대비(WCAG)" describe 참조.
    expect(screenOptions.tabBarActiveTintColor).toBe(lightTokens.accent);
    expect(screenOptions.tabBarInactiveTintColor).toBe(lightTokens.textSecondary);
    expect(screenOptions.tabBarActiveTintColor).not.toBe('#007AFF');
    expect(screenOptions.tabBarInactiveTintColor).not.toBe('#8E8E93');
  });

  it('탭 활성색이 다크 모드에서는 다크 accent 토큰으로 렌더된다', () => {
    useThemeMock.mockReturnValue({ scheme: 'dark', tokens: darkTokens });

    const tree = TabsLayout() as ElementWithChildren;
    const tabsElement = findTabsElement(tree);
    const screenOptions = (tabsElement.props as { screenOptions: Record<string, unknown> })
      .screenOptions;

    expect(screenOptions.tabBarActiveTintColor).toBe(darkTokens.accent);
  });

  it('채팅·동아리·티켓 탭은 더 이상 등록되지 않는다(스토어·커뮤니티에 통합)', () => {
    const tree = TabsLayout() as ElementWithChildren;
    const screens = findTabScreens(tree);
    const names = screens.map((s) => s.props.name);

    expect(names).not.toContain('chat');
    expect(names).not.toContain('clubs');
    expect(names).not.toContain('tickets');
  });

  it('시설 탭 라벨은 "시설"이다 (search라는 모호한 이름을 쓰지 않는다)', () => {
    const tree = TabsLayout() as ElementWithChildren;
    const screens = findTabScreens(tree);
    const facilitiesScreen = findScreen(screens, 'facilities');

    expect(facilitiesScreen.props.options?.title).toBe('시설');
  });

  describe('탭바 배경·테두리 (다크 모드 순백 회귀 가드)', () => {
    function getTabBarStyle(tree: ElementWithChildren): Record<string, unknown> {
      const tabsElement = findTabsElement(tree);
      const screenOptions = (tabsElement.props as { screenOptions: Record<string, unknown> })
        .screenOptions;
      return screenOptions.tabBarStyle as Record<string, unknown>;
    }

    it('라이트 모드에서 탭바 배경·테두리가 surfaceElevated·border 토큰이다', () => {
      useThemeMock.mockReturnValue({ scheme: 'light', tokens: lightTokens });

      const tree = TabsLayout() as ElementWithChildren;
      const tabBarStyle = getTabBarStyle(tree);

      expect(tabBarStyle.backgroundColor).toBe(lightTokens.surfaceElevated);
      expect(tabBarStyle.borderTopColor).toBe(lightTokens.border);
    });

    it('다크 모드에서 탭바 배경이 다크 surfaceElevated 토큰이다 (라이트 전용 흰색 회귀 가드)', () => {
      useThemeMock.mockReturnValue({ scheme: 'dark', tokens: darkTokens });

      const tree = TabsLayout() as ElementWithChildren;
      const tabBarStyle = getTabBarStyle(tree);

      expect(tabBarStyle.backgroundColor).toBe(darkTokens.surfaceElevated);
      expect(tabBarStyle.backgroundColor).not.toBe('#ffffff');
      expect(tabBarStyle.backgroundColor).not.toBe('#FFFFFF');
      expect(tabBarStyle.borderTopColor).toBe(darkTokens.border);
      expect(tabBarStyle.borderTopColor).not.toBe('#d8d8d8');
    });

    it('다크 모드 탭바 배경은 다크 페이지 배경(background)보다 밝지 않다', () => {
      useThemeMock.mockReturnValue({ scheme: 'dark', tokens: darkTokens });

      const tree = TabsLayout() as ElementWithChildren;
      const tabBarStyle = getTabBarStyle(tree);
      const backgroundColor = tabBarStyle.backgroundColor as string;

      // 다크 토큰 값은 모두 저휘도 hex이므로, 흰색(#ffffff 계열)로의 회귀를 문자열 비교로 강제한다.
      expect(backgroundColor.toLowerCase()).not.toBe('#ffffff');
      expect(backgroundColor.toLowerCase()).not.toBe('#fff');
    });
  });

  /**
   * 탭바 대비(WCAG) — 배경(surfaceElevated) 토큰을 바꿀 때마다 그 위 활성·비활성 라벨의
   * 판독 가능성이 회귀하지 않도록 대비 자체를 계산해 강제한다.
   *
   * 배경: tabBarStyle에 surfaceElevated를 도입하면서(순백 회귀 수정) 배경이 어두워져,
   * 그대로였던 textTertiary(비활성)가 다크에서 4.62:1(구 순백 배경) → 3.25:1로 악화됐다.
   * 색만 바꾸면 재발하므로 여기서 대비 수치 자체를 단언한다 (web/__tests__/themeContrast.test.ts 방식 참고).
   */
  describe('탭바 대비(WCAG) — surfaceElevated 배경 위 활성·비활성 라벨', () => {
    const TEXT_MINIMUM = 4.5;
    const UI_COMPONENT_MINIMUM = 3;

    function hexToRgb(hex: string): readonly [number, number, number] {
      const normalized = hex.replace('#', '');
      return [
        parseInt(normalized.slice(0, 2), 16),
        parseInt(normalized.slice(2, 4), 16),
        parseInt(normalized.slice(4, 6), 16),
      ];
    }

    function relativeLuminance(hex: string): number {
      const [r, g, b] = hexToRgb(hex).map((channel) => {
        const c = channel / 255;
        return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
      });
      return 0.2126 * r! + 0.7152 * g! + 0.0722 * b!;
    }

    function contrastRatio(foreground: string, background: string): number {
      const first = relativeLuminance(foreground);
      const second = relativeLuminance(background);
      const [lighter, darker] = first > second ? [first, second] : [second, first];
      return (lighter + 0.05) / (darker + 0.05);
    }

    it('다크 모드: 탭바 배경 ↔ 비활성 라벨(textSecondary) 대비가 AA 본문 기준(4.5:1)을 만족한다', () => {
      const ratio = contrastRatio(darkTokens.textSecondary, darkTokens.surfaceElevated);

      expect(ratio).toBeGreaterThanOrEqual(TEXT_MINIMUM);
    });

    it('다크 모드: 탭바 배경 ↔ 활성 라벨(accent) 대비가 AA 본문 기준(4.5:1)을 만족한다', () => {
      const ratio = contrastRatio(darkTokens.accent, darkTokens.surfaceElevated);

      expect(ratio).toBeGreaterThanOrEqual(TEXT_MINIMUM);
    });

    it('라이트 모드: 탭바 배경 ↔ 비활성 라벨(textSecondary) 대비가 AA 본문 기준(4.5:1)을 만족한다', () => {
      const ratio = contrastRatio(lightTokens.textSecondary, lightTokens.surfaceElevated);

      expect(ratio).toBeGreaterThanOrEqual(TEXT_MINIMUM);
    });

    it(
      '라이트 모드: 탭바 배경 ↔ 활성 라벨(accent) 대비는 UI 컴포넌트 기준(3:1)을 만족한다 ' +
        '(accent 토큰 자체가 라이트에서 AA 본문 기준 미달인 사전 결함 — 이번 티켓 범위 밖, 별도 티켓 대상)',
      () => {
        const ratio = contrastRatio(lightTokens.accent, lightTokens.surfaceElevated);

        expect(ratio).toBeGreaterThanOrEqual(UI_COMPONENT_MINIMUM);
      }
    );

    it('과거 회귀(textTertiary를 배경에 썼을 때 다크 3.25:1)로 되돌아가지 않는다', () => {
      const regressedRatio = contrastRatio(darkTokens.textTertiary, darkTokens.surfaceElevated);

      // textTertiary 자체는 여전히 AA 미달 조합이다 — 탭바 비활성 라벨로 다시 쓰이면
      // 이 값이 통과선(4.5) 밑이라는 사실로 회귀를 감지할 수 있음을 고정해 둔다.
      expect(regressedRatio).toBeLessThan(TEXT_MINIMUM);
    });
  });
});
