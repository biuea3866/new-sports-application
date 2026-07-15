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

  it('탭 활성색·비활성색이 하드코딩이 아닌 accent·textTertiary 토큰으로 렌더된다 (라이트)', () => {
    useThemeMock.mockReturnValue({ scheme: 'light', tokens: lightTokens });

    const tree = TabsLayout() as ElementWithChildren;
    const tabsElement = findTabsElement(tree);
    const screenOptions = (tabsElement.props as { screenOptions: Record<string, unknown> })
      .screenOptions;

    expect(screenOptions.tabBarActiveTintColor).toBe(lightTokens.accent);
    expect(screenOptions.tabBarInactiveTintColor).toBe(lightTokens.textTertiary);
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
});
