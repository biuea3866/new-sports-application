/**
 * HomeScreen — 통합 검색 진입점(FE-11) 게이팅·이동 + 채팅 진입 아이콘 +
 * "다가오는 경기" 카드 탭 시 스토어 탭 티켓 세그먼트 이동을 검증.
 * 근거: FE-11 티켓 "테스트 케이스", design-fe-app.md "라우팅·내비게이션 흐름",
 * 사용자 피드백 "다가오는 경기를 어디서 보는지 모르겠다" + "채팅은 탭에서 제거 →
 * 홈·커뮤니티 화면 상단 우측 아이콘으로 진입".
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

jest.mock('../../../lib/useTotalUnread', () => ({
  useTotalUnread: jest.fn(),
}));

jest.mock('expo-router', () => ({
  useRouter: jest.fn(),
}));

import { useQuery } from '@tanstack/react-query';
import { useRouter } from 'expo-router';
import { isFeatureEnabled } from '../../../lib/feature-flags';
import { useTotalUnread } from '../../../lib/useTotalUnread';
import HomeScreen from '../index';

const useQueryMock = useQuery as jest.MockedFunction<typeof useQuery>;
const useRouterMock = useRouter as jest.MockedFunction<typeof useRouter>;
const isFeatureEnabledMock = isFeatureEnabled as jest.MockedFunction<typeof isFeatureEnabled>;
const useTotalUnreadMock = useTotalUnread as jest.MockedFunction<typeof useTotalUnread>;

const UPCOMING_EVENT = {
  id: 7,
  title: '결승전',
  venue: '잠실 종합운동장',
  startsAt: '2026-08-01T18:00:00Z',
  status: 'OPEN',
};

const UPCOMING_PRODUCT = {
  id: 42,
  name: '유니폼',
  category: 'GOODS',
  price: 59000,
};

function mockQueries(events: unknown[] = [], products: unknown[] = []) {
  useQueryMock.mockImplementation((options: unknown) => {
    const queryKey = (options as { queryKey: unknown[] }).queryKey;
    const data =
      queryKey[0] === 'home-events' ? events : queryKey[0] === 'home-products' ? products : [];
    return { data, isLoading: false, isError: false } as unknown as ReturnType<typeof useQuery>;
  });
}

describe('홈 화면 — 통합 검색 진입점', () => {
  const pushMock = jest.fn();

  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    mockQueries();
    useRouterMock.mockReturnValue({
      push: pushMock,
    } as unknown as ReturnType<typeof useRouter>);
    isFeatureEnabledMock.mockReturnValue(false);
    useTotalUnreadMock.mockReturnValue(0);
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

  it('채팅 진입 아이콘이 렌더되고 탭하면 채팅방 목록으로 이동한다', () => {
    render(<HomeScreen />);
    fireEvent.press(screen.getByLabelText('채팅'));

    expect(pushMock).toHaveBeenCalledWith('/rooms');
  });

  it('다가오는 경기 카드를 탭하면 스토어 탭 티켓 세그먼트로 이동한다', () => {
    mockQueries([UPCOMING_EVENT]);

    render(<HomeScreen />);
    fireEvent.press(screen.getByText('결승전'));

    expect(pushMock).toHaveBeenCalledWith('/(tabs)/store?segment=tickets');
  });

  it('상품 카드를 탭하면 상품 상세로 이동한다', () => {
    mockQueries([], [UPCOMING_PRODUCT]);

    render(<HomeScreen />);
    fireEvent.press(screen.getByText('유니폼'));

    expect(pushMock).toHaveBeenCalledWith('/product/42');
  });

  it('상품 카드는 접근성 버튼 역할을 가진다', () => {
    mockQueries([], [UPCOMING_PRODUCT]);

    render(<HomeScreen />);

    expect(screen.getByLabelText(/유니폼/)).toBeTruthy();
  });
});
