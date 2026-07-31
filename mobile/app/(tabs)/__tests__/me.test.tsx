/**
 * MeScreen — 내 주문 내역(통합) 진입점(FE-11) 게이팅·이동 검증.
 * 근거: FE-11 티켓 "테스트 케이스", design-fe-app.md "라우팅·내비게이션 흐름".
 *
 * useAuthStore를 모킹해, 이 화면에 새로 배선하는 진입점(노출 게이팅·이동)만
 * 사용자 관점으로 검증한다.
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

jest.mock('../../../lib/auth', () => ({
  useAuthStore: jest.fn(),
}));

jest.mock('../../../lib/feature-flags', () => ({
  isFeatureEnabled: jest.fn(),
}));

jest.mock('expo-router', () => ({
  useRouter: jest.fn(),
}));

jest.mock('../../../lib/useMyProfile', () => ({
  useMyProfile: jest.fn(),
}));

import { useAuthStore } from '../../../lib/auth';
import { useRouter } from 'expo-router';
import { isFeatureEnabled } from '../../../lib/feature-flags';
import { useMyProfile } from '../../../lib/useMyProfile';
import MeScreen from '../me';

const useMyProfileMock = useMyProfile as unknown as jest.Mock;

const useAuthStoreMock = useAuthStore as unknown as jest.Mock;
const useRouterMock = useRouter as jest.MockedFunction<typeof useRouter>;
const isFeatureEnabledMock = isFeatureEnabled as jest.MockedFunction<typeof isFeatureEnabled>;

interface MockAuthState {
  accessToken: string | null;
  logout: () => Promise<void>;
}

function mockAuthState(overrides: Partial<MockAuthState> = {}): void {
  const state: MockAuthState = {
    accessToken: null,
    logout: jest.fn().mockResolvedValue(undefined),
    ...overrides,
  };
  useAuthStoreMock.mockImplementation((selector: (s: MockAuthState) => unknown) => selector(state));
}

describe('마이 화면 — 내 주문 내역 진입점', () => {
  const pushMock = jest.fn();
  const replaceMock = jest.fn();

  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    mockAuthState();
    useRouterMock.mockReturnValue({
      push: pushMock,
      replace: replaceMock,
    } as unknown as ReturnType<typeof useRouter>);
    useMyProfileMock.mockReturnValue({ data: undefined, isLoading: false, isError: false });
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('orders.unified.enabled가 ON이면 내 주문 내역 진입점이 보인다', () => {
    isFeatureEnabledMock.mockReturnValue(true);

    render(<MeScreen />);

    expect(screen.getByText('내 주문 내역')).toBeTruthy();
  });

  it('내 주문 내역 진입점을 탭하면 /orders로 이동한다', () => {
    isFeatureEnabledMock.mockReturnValue(true);

    render(<MeScreen />);
    fireEvent.press(screen.getByLabelText('내 주문 내역'));

    expect(pushMock).toHaveBeenCalledWith('/orders');
  });

  it('orders.unified.enabled가 OFF면 내 주문 내역 진입점이 보이지 않는다', () => {
    isFeatureEnabledMock.mockReturnValue(false);

    render(<MeScreen />);

    expect(screen.queryByText('내 주문 내역')).toBeNull();
  });

  it('다크 모드에서도 진입점이 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');
    isFeatureEnabledMock.mockReturnValue(true);

    render(<MeScreen />);

    expect(screen.getByText('내 주문 내역')).toBeTruthy();
  });
});

describe('마이 화면 — 내 정보 표시', () => {
  const profile = {
    id: 68,
    email: 'demo.user@sportsapp.dev',
    status: 'ACTIVE' as const,
    createdAt: '2026-07-31T05:00:00Z',
  };

  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    isFeatureEnabledMock.mockReturnValue(false);
    useRouterMock.mockReturnValue({
      push: jest.fn(),
      replace: jest.fn(),
    } as unknown as ReturnType<typeof useRouter>);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  // 앱을 새로고침하면 메모리에만 있던 accessToken이 사라진다 — 그때도 내 정보는 보여야 한다.
  it('토큰이 메모리에 없어도 서버 프로필로 이메일과 사용자 ID를 표시한다', () => {
    mockAuthState({ accessToken: null });
    useMyProfileMock.mockReturnValue({ data: profile, isLoading: false, isError: false });

    render(<MeScreen />);

    expect(screen.getByText('demo.user@sportsapp.dev')).toBeTruthy();
    expect(screen.getByText('68')).toBeTruthy();
  });

  it('프로필을 불러오는 중에는 안내 문구를 표시한다', () => {
    mockAuthState({ accessToken: null });
    useMyProfileMock.mockReturnValue({ data: undefined, isLoading: true, isError: false });

    render(<MeScreen />);

    // 이메일·사용자 ID 두 필드가 같은 상태 문구를 보여준다.
    expect(screen.getAllByText('불러오는 중...').length).toBeGreaterThan(0);
  });

  it('프로필 조회에 실패하면 안내 문구를 표시한다', () => {
    mockAuthState({ accessToken: null });
    useMyProfileMock.mockReturnValue({ data: undefined, isLoading: false, isError: true });

    render(<MeScreen />);

    expect(screen.getAllByText('정보를 불러오지 못했어요').length).toBeGreaterThan(0);
  });
});
