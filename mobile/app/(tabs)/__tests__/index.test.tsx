/**
 * HomeScreen — 통합 검색 진입점(FE-11) 게이팅·이동 검증.
 * 근거: FE-11 티켓 "테스트 케이스", design-fe-app.md "라우팅·내비게이션 흐름".
 *
 * 이벤트/상품 조회(useQuery)는 정적 반환으로 모킹해, 이 화면에 새로 배선하는
 * 진입점(노출 게이팅·이동)만 사용자 관점으로 검증한다.
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

jest.mock('@tanstack/react-query', () => ({
  useQuery: jest.fn(),
}));

jest.mock('../../../lib/feature-flags', () => ({
  isFeatureEnabled: jest.fn(),
}));

jest.mock('expo-router', () => ({
  useRouter: jest.fn(),
}));

import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { isFeatureEnabled } from '../../../lib/feature-flags';
import HomeScreen from '../index';

const useQueryMock = useQuery as jest.MockedFunction<typeof useQuery>;
const useRouterMock = useRouter as jest.MockedFunction<typeof useRouter>;
const isFeatureEnabledMock = isFeatureEnabled as jest.MockedFunction<typeof isFeatureEnabled>;

describe('홈 화면 — 통합 검색 진입점', () => {
  const pushMock = jest.fn();

  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    useQueryMock.mockReturnValue({
      data: [],
      isLoading: false,
      isError: false,
    } as unknown as ReturnType<typeof useQuery>);
    useRouterMock.mockReturnValue({
      push: pushMock,
    } as unknown as ReturnType<typeof useRouter>);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('catalog.enabled가 ON이면 통합 검색 진입점이 보인다', () => {
    isFeatureEnabledMock.mockReturnValue(true);

    render(<HomeScreen />);

    expect(screen.getByText('통합 검색')).toBeTruthy();
  });

  it('통합 검색 진입점을 탭하면 /catalog로 이동한다', () => {
    isFeatureEnabledMock.mockReturnValue(true);

    render(<HomeScreen />);
    fireEvent.press(screen.getByLabelText('통합 검색'));

    expect(pushMock).toHaveBeenCalledWith('/catalog');
  });

  it('catalog.enabled가 OFF면 통합 검색 진입점이 보이지 않는다', () => {
    isFeatureEnabledMock.mockReturnValue(false);

    render(<HomeScreen />);

    expect(screen.queryByText('통합 검색')).toBeNull();
  });

  it('다크 모드에서도 진입점이 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');
    isFeatureEnabledMock.mockReturnValue(true);

    render(<HomeScreen />);

    expect(screen.getByText('통합 검색')).toBeTruthy();
  });
});
