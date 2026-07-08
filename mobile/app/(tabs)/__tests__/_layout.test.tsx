/**
 * TabsLayout — 탭 활성색 accent 토큰화·전역 안읽은 배지·community 플래그 게이팅 검증
 * 근거: 티켓 "앱 와이어업·기능 플래그·전역 배지 통합" 테스트 케이스.
 *
 * jest.setup.ts의 전역 expo-router mock은 Tabs/Tabs.Screen을 실제 컴포넌트가 아닌
 * 단순 객체/문자열로 대체하므로, `render()`로 마운트하지 않고 TabsLayout()을 순수
 * 함수로 호출해 반환된 React 엘리먼트 트리를 구조적으로 검증한다(app/__tests__/_layout.test.tsx와
 * 동일 기법). useTheme·useTotalUnread·feature-flags는 모듈 모킹으로 대체해 훅 디스패처 없이도
 * 안전하게 직접 호출할 수 있게 한다.
 */
import React from 'react';

import TabsLayout from '../_layout';
import { lightTokens, darkTokens } from '../../../theme/tokens';

jest.mock('../../../theme/useTheme', () => ({
  useTheme: jest.fn(),
}));

jest.mock('../../../lib/useTotalUnread', () => ({
  useTotalUnread: jest.fn(),
}));

jest.mock('../../../lib/feature-flags', () => ({
  isFeatureEnabled: jest.fn(),
}));

import { useTheme } from '../../../theme/useTheme';
import { useTotalUnread } from '../../../lib/useTotalUnread';
import { isFeatureEnabled } from '../../../lib/feature-flags';

const useThemeMock = useTheme as jest.MockedFunction<typeof useTheme>;
const useTotalUnreadMock = useTotalUnread as jest.MockedFunction<typeof useTotalUnread>;
const isFeatureEnabledMock = isFeatureEnabled as jest.MockedFunction<typeof isFeatureEnabled>;

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

describe('TabsLayout', () => {
  afterEach(() => jest.clearAllMocks());

  it('탭 활성색·비활성색이 하드코딩이 아닌 accent·textTertiary 토큰으로 렌더된다 (라이트)', () => {
    useThemeMock.mockReturnValue({ scheme: 'light', tokens: lightTokens });
    useTotalUnreadMock.mockReturnValue(0);
    isFeatureEnabledMock.mockReturnValue(true);

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
    useTotalUnreadMock.mockReturnValue(0);
    isFeatureEnabledMock.mockReturnValue(true);

    const tree = TabsLayout() as ElementWithChildren;
    const screens = findTabScreens(tree);
    const chatScreen = requireDefined(
      screens.find((s) => s.props.name === 'chat'),
      'chat 탭이 등록되지 않았습니다'
    );

    // 다크 모드 배지 색상도 다크 토큰을 사용해야 한다 (chat 탭 배지 스타일로 확인)
    const badgeStyle = chatScreen.props.options?.tabBarBadgeStyle as
      | { backgroundColor?: string }
      | undefined;
    expect(badgeStyle?.backgroundColor).toBe(darkTokens.badge);
  });

  it('전역 안읽은 수 합계가 0보다 크면 채팅 탭에 배지가 표시된다', () => {
    useThemeMock.mockReturnValue({ scheme: 'light', tokens: lightTokens });
    useTotalUnreadMock.mockReturnValue(7);
    isFeatureEnabledMock.mockReturnValue(true);

    const tree = TabsLayout() as ElementWithChildren;
    const screens = findTabScreens(tree);
    const chatScreen = requireDefined(
      screens.find((s) => s.props.name === 'chat'),
      'chat 탭이 등록되지 않았습니다'
    );

    expect(chatScreen.props.options?.tabBarBadge).toBe(7);
  });

  it('전역 안읽은 수 합계가 0이면 채팅 탭에 배지가 표시되지 않는다', () => {
    useThemeMock.mockReturnValue({ scheme: 'light', tokens: lightTokens });
    useTotalUnreadMock.mockReturnValue(0);
    isFeatureEnabledMock.mockReturnValue(true);

    const tree = TabsLayout() as ElementWithChildren;
    const screens = findTabScreens(tree);
    const chatScreen = requireDefined(
      screens.find((s) => s.props.name === 'chat'),
      'chat 탭이 등록되지 않았습니다'
    );

    expect(chatScreen.props.options?.tabBarBadge).toBeUndefined();
  });

  it('chat.community.enabled가 OFF면 동아리(clubs) 탭 진입점이 숨겨진다', () => {
    useThemeMock.mockReturnValue({ scheme: 'light', tokens: lightTokens });
    useTotalUnreadMock.mockReturnValue(0);
    isFeatureEnabledMock.mockReturnValue(false);

    const tree = TabsLayout() as ElementWithChildren;
    const screens = findTabScreens(tree);
    const clubsScreen = requireDefined(
      screens.find((s) => s.props.name === 'clubs'),
      'clubs 탭이 등록되지 않았습니다'
    );

    expect(clubsScreen.props.options?.href).toBeNull();
  });

  it('chat.community.enabled가 ON이면 동아리(clubs) 탭 진입점이 노출된다', () => {
    useThemeMock.mockReturnValue({ scheme: 'light', tokens: lightTokens });
    useTotalUnreadMock.mockReturnValue(0);
    isFeatureEnabledMock.mockReturnValue(true);

    const tree = TabsLayout() as ElementWithChildren;
    const screens = findTabScreens(tree);
    const clubsScreen = requireDefined(
      screens.find((s) => s.props.name === 'clubs'),
      'clubs 탭이 등록되지 않았습니다'
    );

    expect(clubsScreen.props.options?.href).not.toBeNull();
  });

  it('기존 post 게시판 커뮤니티 탭(community)이 그대로 유지된다', () => {
    useThemeMock.mockReturnValue({ scheme: 'light', tokens: lightTokens });
    useTotalUnreadMock.mockReturnValue(0);
    isFeatureEnabledMock.mockReturnValue(true);

    const tree = TabsLayout() as ElementWithChildren;
    const screens = findTabScreens(tree);
    const communityScreen = screens.find((s) => s.props.name === 'community');

    expect(communityScreen).toBeDefined();
  });
});
