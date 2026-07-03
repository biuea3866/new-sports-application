/**
 * RootLayout — 한정판 라우트(Stack.Screen) 등록 검증
 * 근거: FE-08 티켓 "테스트 케이스" (Stack에 등록된 두 라우트가 헤더 없이 렌더된다)
 *
 * jest.setup.ts의 전역 expo-router mock은 Stack을 `{ Screen: 'Screen' }` 객체로 대체해
 * 실제 네비게이터로 렌더되지 않는다. 따라서 RootLayout()을 렌더 대신 순수 함수로 호출해
 * 반환된 React 엘리먼트 트리에서 Stack.Screen 등록 여부·options를 구조적으로 검증한다.
 */
import React from 'react';

import RootLayout from '../_layout';

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
});
