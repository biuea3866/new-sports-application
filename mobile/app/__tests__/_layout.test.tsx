/**
 * RootLayout — 한정판 라우트(Stack.Screen) 등록 검증 + 테마 프로바이더 래핑·
 * 채팅 시스템 신규 라우트 등록 검증(앱 와이어업 통합 티켓)
 *
 * jest.setup.ts의 전역 expo-router mock은 Stack을 `{ Screen: 'Screen' }` 객체로 대체해
 * 실제 네비게이터로 렌더되지 않는다. 따라서 RootLayout()을 렌더 대신 순수 함수로 호출해
 * 반환된 React 엘리먼트 트리에서 Stack.Screen 등록 여부·options를 구조적으로 검증한다.
 */
import React from 'react';

import RootLayout from '../_layout';
import { ThemeProvider } from '../../theme/ThemeProvider';

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

describe('RootLayout 라우트 등록', () => {
  it('한정판 상세·구매 라우트를 헤더 없이 등록한다', () => {
    const tree = RootLayout() as ElementWithChildren;

    const stackElement = requireDefined(
      findDescendant(
        tree,
        (el) => (el.props as { screenOptions?: unknown }).screenOptions !== undefined
      ) as ElementWithChildren | undefined,
      'Stack 엘리먼트를 찾지 못했습니다'
    );

    const screens = React.Children.toArray(stackElement.props.children) as React.ReactElement<{
      name?: string;
      options?: { headerShown?: boolean };
    }>[];

    const detailScreen = requireDefined(
      screens.find((s) => s.props.name === 'limited-drop/[id]/index'),
      'limited-drop/[id]/index 라우트가 등록되지 않았습니다'
    );
    const purchaseScreen = requireDefined(
      screens.find((s) => s.props.name === 'limited-drop/[id]/purchase'),
      'limited-drop/[id]/purchase 라우트가 등록되지 않았습니다'
    );

    expect(detailScreen.props.options?.headerShown).toBe(false);
    expect(purchaseScreen.props.options?.headerShown).toBe(false);
  });

  it('채팅 시스템 신규 라우트(rooms·communities·invite·invitations)를 등록한다', () => {
    const tree = RootLayout() as ElementWithChildren;

    const stackElement = requireDefined(
      findDescendant(
        tree,
        (el) => (el.props as { screenOptions?: unknown }).screenOptions !== undefined
      ) as ElementWithChildren | undefined,
      'Stack 엘리먼트를 찾지 못했습니다'
    );

    const screens = React.Children.toArray(stackElement.props.children) as React.ReactElement<{
      name?: string;
      options?: { headerShown?: boolean };
    }>[];

    const expectedRouteNames = [
      'rooms/index',
      'rooms/[id]',
      'communities/index',
      'communities/new',
      'communities/[id]',
      'invite/[roomId]',
      'invitations/index',
    ];

    for (const routeName of expectedRouteNames) {
      const screen = requireDefined(
        screens.find((s) => s.props.name === routeName),
        `${routeName} 라우트가 등록되지 않았습니다`
      );
      expect(screen.props.options?.headerShown).toBe(false);
    }
  });

  it('앱 루트가 ThemeProvider로 래핑되어 하위 화면(AuthGuard·Stack)이 그 안에 있다', () => {
    const tree = RootLayout() as ElementWithChildren;

    const themeProviderElement = requireDefined(
      findDescendant(tree, (el) => el.type === ThemeProvider) as ElementWithChildren | undefined,
      'ThemeProvider가 트리에 없습니다'
    );

    const stackInsideThemeProvider = findDescendant(
      themeProviderElement,
      (el) => (el.props as { screenOptions?: unknown }).screenOptions !== undefined
    );

    expect(stackInsideThemeProvider).toBeDefined();
  });
});
