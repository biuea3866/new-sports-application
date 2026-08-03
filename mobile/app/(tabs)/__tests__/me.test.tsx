/**
 * MeScreen — 내 주문 내역(통합) 진입점(FE-11) 게이팅·이동 검증.
 * 근거: FE-11 티켓 "테스트 케이스", design-fe-app.md "라우팅·내비게이션 흐름".
 *
 * useAuthStore를 모킹해, 이 화면에 새로 배선하는 진입점(노출 게이팅·이동)만
 * 사용자 관점으로 검증한다.
 */
import React from 'react';
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { AxiosError } from 'axios';

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
  useChangeMyNickname: jest.fn(),
}));

import { useAuthStore } from '../../../lib/auth';
import { useRouter } from 'expo-router';
import { isFeatureEnabled } from '../../../lib/feature-flags';
import { useChangeMyNickname, useMyProfile } from '../../../lib/useMyProfile';
import MeScreen from '../me';

const useMyProfileMock = useMyProfile as unknown as jest.Mock;
const changeMyNicknameMock = useChangeMyNickname as unknown as jest.Mock;

const useAuthStoreMock = useAuthStore as unknown as jest.Mock;
const useRouterMock = useRouter as jest.MockedFunction<typeof useRouter>;
const isFeatureEnabledMock = isFeatureEnabled as jest.MockedFunction<typeof isFeatureEnabled>;

interface MockAuthState {
  accessToken: string | null;
  logout: () => Promise<void>;
}

/** 테스트용 JWT — 서명 검증은 하지 않고 payload만 디코딩되므로 base64url payload면 충분하다. */
function buildAccessToken(payload: Record<string, unknown>): string {
  const encoded = Buffer.from(JSON.stringify(payload), 'utf-8')
    .toString('base64')
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return `header.${encoded}.signature`;
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
    changeMyNicknameMock.mockReturnValue({ mutate: jest.fn(), isPending: false });
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
    changeMyNicknameMock.mockReturnValue({ mutate: jest.fn(), isPending: false });
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

  it('역할을 영문 enum 원문이 아니라 한글로 표시한다', () => {
    mockAuthState({ accessToken: null });
    useMyProfileMock.mockReturnValue({ data: profile, isLoading: false, isError: false });

    render(<MeScreen />);

    expect(screen.getByText('일반 회원')).toBeTruthy();
    expect(screen.queryByText('USER')).toBeNull();
  });

  it('여러 역할을 가지면 한글 라벨을 이어서 표시한다', () => {
    mockAuthState({ accessToken: buildAccessToken({ roles: ['USER', 'FACILITY_OWNER'] }) });
    useMyProfileMock.mockReturnValue({ data: profile, isLoading: false, isError: false });

    render(<MeScreen />);

    expect(screen.getByText('일반 회원, 시설 운영자')).toBeTruthy();
  });

  it('알 수 없는 역할 값은 원문 그대로 보여준다(정보 손실 방지)', () => {
    mockAuthState({ accessToken: buildAccessToken({ roles: ['NEW_ROLE'] }) });
    useMyProfileMock.mockReturnValue({ data: profile, isLoading: false, isError: false });

    render(<MeScreen />);

    expect(screen.getByText('NEW_ROLE')).toBeTruthy();
  });
});

describe('마이 화면 — 닉네임', () => {
  const profileWithNickname = {
    id: 68,
    email: 'demo.user@sportsapp.dev',
    nickname: '김철수',
    displayName: '김철수',
    status: 'ACTIVE' as const,
    createdAt: '2026-07-31T05:00:00Z',
  };

  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    isFeatureEnabledMock.mockReturnValue(false);
    mockAuthState({ accessToken: null });
    useRouterMock.mockReturnValue({
      push: jest.fn(),
      replace: jest.fn(),
    } as unknown as ReturnType<typeof useRouter>);
    changeMyNicknameMock.mockReset();
    changeMyNicknameMock.mockReturnValue({ mutate: jest.fn(), isPending: false });
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('설정된 닉네임을 보여준다', () => {
    useMyProfileMock.mockReturnValue({ data: profileWithNickname, isLoading: false, isError: false });

    render(<MeScreen />);

    expect(screen.getByText('김철수')).toBeTruthy();
  });

  it('닉네임이 없으면 설정을 유도한다', () => {
    useMyProfileMock.mockReturnValue({
      data: { ...profileWithNickname, nickname: null, displayName: '닉네임 미설정' },
      isLoading: false,
      isError: false,
    });

    render(<MeScreen />);

    expect(screen.getByText('닉네임을 설정해 주세요')).toBeTruthy();
  });

  it('닉네임을 수정해 저장하면 변경 요청을 보낸다', async () => {
    const mutateMock = jest.fn();
    changeMyNicknameMock.mockReturnValue({ mutate: mutateMock, isPending: false });
    useMyProfileMock.mockReturnValue({ data: profileWithNickname, isLoading: false, isError: false });

    render(<MeScreen />);
    fireEvent.press(screen.getByLabelText('닉네임 수정'));
    fireEvent.changeText(screen.getByLabelText('닉네임 입력'), '박영희');
    fireEvent.press(screen.getByLabelText('닉네임 저장'));

    await waitFor(() => {
      expect(mutateMock).toHaveBeenCalledWith('박영희', expect.anything());
    });
  });

  it('닉네임을 비운 채 저장하면 요청하지 않고 안내한다', async () => {
    const mutateMock = jest.fn();
    changeMyNicknameMock.mockReturnValue({ mutate: mutateMock, isPending: false });
    useMyProfileMock.mockReturnValue({ data: profileWithNickname, isLoading: false, isError: false });

    render(<MeScreen />);
    fireEvent.press(screen.getByLabelText('닉네임 수정'));
    fireEvent.changeText(screen.getByLabelText('닉네임 입력'), '   ');
    fireEvent.press(screen.getByLabelText('닉네임 저장'));

    await waitFor(() => {
      expect(screen.getByText('닉네임을 입력해 주세요')).toBeTruthy();
    });
    expect(mutateMock).not.toHaveBeenCalled();
  });

  it('프로필을 불러오는 중에는 닉네임 자리에 안내 문구를 표시하고 수정을 막는다', () => {
    useMyProfileMock.mockReturnValue({ data: undefined, isLoading: true, isError: false });

    render(<MeScreen />);

    expect(screen.queryByText('닉네임을 설정해 주세요')).toBeNull();
    // 닉네임·이메일·사용자 ID 3필드가 같은 로딩 표기를 쓴다 (형제 필드와 4상태 일치)
    expect(screen.getAllByText('불러오는 중...')).toHaveLength(3);
    expect(screen.getByLabelText('닉네임 수정').props.accessibilityState.disabled).toBe(true);
  });

  it('프로필 조회에 실패하면 미설정으로 오인시키지 않고 수정을 막는다', () => {
    useMyProfileMock.mockReturnValue({ data: undefined, isLoading: false, isError: true });

    render(<MeScreen />);

    expect(screen.queryByText('닉네임을 설정해 주세요')).toBeNull();
    // 닉네임·이메일·사용자 ID 3필드가 같은 실패 표기를 쓴다 (형제 필드와 4상태 일치)
    expect(screen.getAllByText('정보를 불러오지 못했어요')).toHaveLength(3);
    expect(screen.getByLabelText('닉네임 수정').props.accessibilityState.disabled).toBe(true);
  });

  it('닉네임 변경이 규칙 위반(400)으로 실패하면 규칙 안내를 보여준다', async () => {
    const mutateMock = jest.fn((_nickname: string, options: { onError: (e: unknown) => void }) => {
      options.onError(
        new AxiosError('boom', undefined, undefined, undefined, {
          status: 400,
          data: { properties: { code: 'INVALID_NICKNAME' } },
          statusText: '',
          headers: {},
          config: {} as never,
        })
      );
    });
    changeMyNicknameMock.mockReturnValue({ mutate: mutateMock, isPending: false });
    useMyProfileMock.mockReturnValue({ data: profileWithNickname, isLoading: false, isError: false });

    render(<MeScreen />);
    fireEvent.press(screen.getByLabelText('닉네임 수정'));
    fireEvent.changeText(screen.getByLabelText('닉네임 입력'), 'x');
    fireEvent.press(screen.getByLabelText('닉네임 저장'));

    await waitFor(() => {
      expect(screen.getByText(/닉네임은 한글·영문·숫자·밑줄 2~20자/)).toBeTruthy();
    });
  });

  it('다크 모드에서도 닉네임이 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');
    useMyProfileMock.mockReturnValue({ data: profileWithNickname, isLoading: false, isError: false });

    render(<MeScreen />);

    expect(screen.getByText('김철수')).toBeTruthy();
  });
});
