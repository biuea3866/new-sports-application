/**
 * CommunityDetailScreen(S5) — 커뮤니티 상세·가입·멤버·역할 관리 화면 사용자 관점 동작 검증.
 * 근거: FE-12 티켓 "테스트 케이스", design-fe-app.md S5 와이어프레임·"화면별 상태 표"·"권한별".
 *
 * useCommunity·useCommunityMembers·useMyProfile·멤버십 mutation 훅을 모킹해 화면 배선만 검증한다.
 */
import React from 'react';
import { Alert } from 'react-native';
import { fireEvent, render, screen } from '@testing-library/react-native';
import { AxiosError } from 'axios';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import type { CommunityMemberResponse, CommunityResponse } from '../../../api/community-types';
import type { MyProfileResponse } from '../../../api/types';
import { MY_ROOMS_QUERY_KEY } from '../../../lib/useRooms';

jest.mock('../../../lib/useCommunity', () => ({
  useCommunity: jest.fn(),
  useCommunityMembers: jest.fn(),
  useJoinCommunity: jest.fn(),
  useKickMember: jest.fn(),
  useTransferHost: jest.fn(),
  useLeaveCommunity: jest.fn(),
}));

jest.mock('../../../lib/useMyProfile', () => ({
  useMyProfile: jest.fn(),
}));

jest.mock('../../../lib/feature-flags', () => ({
  isFeatureEnabled: jest.fn(),
}));

jest.mock('expo-router', () => ({
  useLocalSearchParams: jest.fn(),
  router: { push: jest.fn(), back: jest.fn(), replace: jest.fn() },
}));

// 게시판·소모임예약 섹션은 각자 __tests__(CommunityBoardSection.test.tsx·
// CommunityBookingSection.test.tsx)에서 4상태를 전수 검증한다. 이 화면 테스트는 탭 전환·
// props 배선(communityId·canWrite·canLink·콜백)만 스텁으로 검증한다.
jest.mock('../../../components/community/CommunityBoardSection', () => ({
  CommunityBoardSection: (props: {
    communityId: number;
    canWrite: boolean;
    onCreatePost: () => void;
    onPostPress: (postId: number) => void;
  }) => {
    const { Pressable, Text } = jest.requireActual('react-native');
    return (
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`게시판 섹션 communityId=${props.communityId} canWrite=${props.canWrite}`}
        onPress={() => {
          props.onCreatePost();
          props.onPostPress(999);
        }}
      >
        <Text>게시판 섹션 스텁</Text>
      </Pressable>
    );
  },
}));

jest.mock('../../../components/community/CommunityBookingSection', () => ({
  CommunityBookingSection: (props: {
    communityId: number;
    canLink: boolean;
    onLinkPress: () => void;
  }) => {
    const { Pressable, Text } = jest.requireActual('react-native');
    return (
      <Pressable
        accessibilityRole="button"
        accessibilityLabel={`활동 섹션 communityId=${props.communityId} canLink=${props.canLink}`}
        onPress={props.onLinkPress}
      >
        <Text>활동 섹션 스텁</Text>
      </Pressable>
    );
  },
}));

import { router, useLocalSearchParams } from 'expo-router';
import {
  useCommunity,
  useCommunityMembers,
  useJoinCommunity,
  useKickMember,
  useLeaveCommunity,
  useTransferHost,
} from '../../../lib/useCommunity';
import { useMyProfile } from '../../../lib/useMyProfile';
import { isFeatureEnabled } from '../../../lib/feature-flags';
import CommunityDetailScreen from '../[id]';

const useCommunityMock = useCommunity as jest.MockedFunction<typeof useCommunity>;
const useCommunityMembersMock = useCommunityMembers as jest.MockedFunction<
  typeof useCommunityMembers
>;
const useJoinCommunityMock = useJoinCommunity as jest.MockedFunction<typeof useJoinCommunity>;
const useKickMemberMock = useKickMember as jest.MockedFunction<typeof useKickMember>;
const useTransferHostMock = useTransferHost as jest.MockedFunction<typeof useTransferHost>;
const useLeaveCommunityMock = useLeaveCommunity as jest.MockedFunction<typeof useLeaveCommunity>;
const useMyProfileMock = useMyProfile as jest.MockedFunction<typeof useMyProfile>;
const useLocalSearchParamsMock = useLocalSearchParams as jest.MockedFunction<
  typeof useLocalSearchParams
>;
const isFeatureEnabledMock = isFeatureEnabled as jest.MockedFunction<typeof isFeatureEnabled>;
const routerPushMock = router.push as jest.MockedFunction<typeof router.push>;
const routerBackMock = router.back as jest.MockedFunction<typeof router.back>;

const MY_USER_ID = 1;
const HOST_USER_ID = 10;
const OTHER_MEMBER_USER_ID = 2;

const baseCommunity: CommunityResponse = {
  id: 5,
  name: '주말 축구 모임',
  description: '동네에서 주말마다 축구해요',
  visibility: 'PUBLIC',
  sportCategory: 'SOCCER',
  hostUserId: HOST_USER_ID,
  memberCount: 2,
  roomId: 77,
  createdAt: '2026-07-01T00:00:00Z',
};

const myProfile: MyProfileResponse = {
  id: MY_USER_ID,
  email: 'me@example.com',
  status: 'ACTIVE',
  createdAt: '2026-01-01T00:00:00Z',
};

function member(overrides: Partial<CommunityMemberResponse> = {}): CommunityMemberResponse {
  return {
    id: 1,
    communityId: baseCommunity.id,
    userId: OTHER_MEMBER_USER_ID,
    role: 'MEMBER',
    status: 'ACTIVE',
    joinedAt: '2026-07-01T00:00:00Z',
    ...overrides,
  };
}

function mockCommunity(overrides: Partial<ReturnType<typeof useCommunity>> = {}) {
  useCommunityMock.mockReturnValue({
    data: baseCommunity,
    isLoading: false,
    isError: false,
    error: null,
    refetch: jest.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useCommunity>);
}

function mockMembers(overrides: Partial<ReturnType<typeof useCommunityMembers>> = {}) {
  useCommunityMembersMock.mockReturnValue({
    data: [],
    isLoading: false,
    isError: false,
    error: null,
    refetch: jest.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useCommunityMembers>);
}

function mockMyProfile(overrides: Partial<ReturnType<typeof useMyProfile>> = {}) {
  useMyProfileMock.mockReturnValue({
    data: myProfile,
    isLoading: false,
    isError: false,
    error: null,
    refetch: jest.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useMyProfile>);
}

function mockMutations() {
  useJoinCommunityMock.mockReturnValue({
    mutate: jest.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useJoinCommunity>);
  useKickMemberMock.mockReturnValue({
    mutate: jest.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useKickMember>);
  useTransferHostMock.mockReturnValue({
    mutate: jest.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useTransferHost>);
  useLeaveCommunityMock.mockReturnValue({
    mutate: jest.fn(),
    isPending: false,
  } as unknown as ReturnType<typeof useLeaveCommunity>);
}

function renderScreen() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const invalidateSpy = jest.spyOn(queryClient, 'invalidateQueries');

  function Wrapped() {
    return (
      <QueryClientProvider client={queryClient}>
        <CommunityDetailScreen />
      </QueryClientProvider>
    );
  }

  const utils = render(<Wrapped />);

  return {
    ...utils,
    invalidateSpy,
    rerenderScreen: () => utils.rerender(<Wrapped />),
  };
}

describe('CommunityDetailScreen', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    useLocalSearchParamsMock.mockReturnValue({ id: String(baseCommunity.id) });
    isFeatureEnabledMock.mockReturnValue(true);
    mockCommunity();
    mockMembers();
    mockMyProfile();
    mockMutations();
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('조회 중이면 로딩 상태를 표시한다', () => {
    mockCommunity({ isLoading: true, data: undefined });

    renderScreen();

    expect(screen.getByLabelText('로딩 중')).toBeTruthy();
  });

  it('커뮤니티 조회 실패 시 에러 뷰가 표시되고 재시도를 탭하면 refetch한다', () => {
    const refetch = jest.fn();
    mockCommunity({ isError: true, error: new Error('boom'), data: undefined, refetch });

    renderScreen();
    fireEvent.press(screen.getByLabelText('다시 시도'));

    expect(refetch).toHaveBeenCalled();
  });

  it('커뮤니티가 없으면 안내 문구를 표시한다', () => {
    mockCommunity({ data: undefined });

    renderScreen();

    expect(screen.getByText('커뮤니티를 찾을 수 없어요')).toBeTruthy();
  });

  it('방장에게는 다른 멤버별 강퇴·위임 버튼이 노출된다', () => {
    mockMembers({
      data: [
        member({ id: 1, userId: MY_USER_ID, role: 'HOST' }),
        member({ id: 2, userId: OTHER_MEMBER_USER_ID, role: 'MEMBER' }),
      ],
    });

    renderScreen();
    fireEvent.press(screen.getByLabelText('멤버'));

    expect(screen.getByLabelText(`사용자 #${OTHER_MEMBER_USER_ID} 강퇴`)).toBeTruthy();
    expect(screen.getByLabelText(`사용자 #${OTHER_MEMBER_USER_ID} 방장 위임`)).toBeTruthy();
  });

  it('일반 멤버에게는 강퇴·위임 버튼이 노출되지 않는다', () => {
    mockMembers({
      data: [
        member({ id: 1, userId: HOST_USER_ID, role: 'HOST' }),
        member({ id: 2, userId: MY_USER_ID, role: 'MEMBER' }),
      ],
    });

    renderScreen();
    fireEvent.press(screen.getByLabelText('멤버'));

    expect(screen.queryByLabelText(`사용자 #${HOST_USER_ID} 강퇴`)).toBeNull();
  });

  it('비공개 커뮤니티 가입 시 "승인 대기 중" 상태가 표시된다', () => {
    mockCommunity({ data: { ...baseCommunity, visibility: 'PRIVATE' } });
    useJoinCommunityMock.mockReturnValue({
      mutate: (
        _variables: void,
        options?: { onSuccess?: (result: { status: 'ACTIVE' | 'PENDING_APPROVAL' }) => void }
      ) => options?.onSuccess?.({ status: 'PENDING_APPROVAL' }),
      isPending: false,
    } as unknown as ReturnType<typeof useJoinCommunity>);

    renderScreen();
    fireEvent.press(screen.getByLabelText('가입하기'));

    expect(screen.getByLabelText('승인 대기 중')).toBeTruthy();
  });

  it('공개 커뮤니티 가입 시 즉시 ACTIVE가 되고 "채팅 입장"이 노출된다', () => {
    useJoinCommunityMock.mockReturnValue({
      mutate: (
        _variables: void,
        options?: { onSuccess?: (result: { status: 'ACTIVE' | 'PENDING_APPROVAL' }) => void }
      ) => {
        mockMembers({ data: [member({ id: 1, userId: MY_USER_ID, role: 'MEMBER' })] });
        options?.onSuccess?.({ status: 'ACTIVE' });
      },
      isPending: false,
    } as unknown as ReturnType<typeof useJoinCommunity>);

    const { rerenderScreen } = renderScreen();
    fireEvent.press(screen.getByLabelText('가입하기'));
    rerenderScreen();

    expect(screen.getByLabelText('채팅 입장')).toBeTruthy();
  });

  it('채팅 입장을 탭하면 전용 방으로 이동한다', () => {
    mockMembers({ data: [member({ id: 1, userId: MY_USER_ID, role: 'MEMBER' })] });

    renderScreen();
    fireEvent.press(screen.getByLabelText('채팅 입장'));

    expect(routerPushMock).toHaveBeenCalledWith(`/rooms/${baseCommunity.roomId}`);
  });

  it('BE 응답에 roomId 필드가 없으면(undefined) 채팅 입장 시 안내 알림을 띄우고 이동하지 않는다', () => {
    mockCommunity({
      data: { ...baseCommunity, roomId: undefined } as unknown as CommunityResponse,
    });
    mockMembers({ data: [member({ id: 1, userId: MY_USER_ID, role: 'MEMBER' })] });
    const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation(() => {});

    renderScreen();
    fireEvent.press(screen.getByLabelText('채팅 입장'));

    expect(alertSpy).toHaveBeenCalledWith('채팅방 안내', '채팅방이 아직 없습니다');
    expect(routerPushMock).not.toHaveBeenCalledWith(expect.stringContaining('/rooms/'));
  });

  it('뒤로가기 버튼을 탭하면 이전 화면으로 이동한다', () => {
    renderScreen();
    fireEvent.press(screen.getByLabelText('뒤로 가기'));

    expect(routerBackMock).toHaveBeenCalled();
  });

  it('강퇴 확인 후 성공 시 방목록 캐시가 무효화된다(자동 퇴장)', () => {
    mockMembers({
      data: [
        member({ id: 1, userId: MY_USER_ID, role: 'HOST' }),
        member({ id: 2, userId: OTHER_MEMBER_USER_ID, role: 'MEMBER' }),
      ],
    });
    const kickMutate = jest.fn(
      (_variables: { userId: number }, options?: { onSuccess?: () => void }) =>
        options?.onSuccess?.()
    );
    useKickMemberMock.mockReturnValue({
      mutate: kickMutate,
      isPending: false,
    } as unknown as ReturnType<typeof useKickMember>);
    const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation((_title, _message, buttons) => {
      buttons?.find((button) => button.text === '강퇴')?.onPress?.();
    });

    const { invalidateSpy } = renderScreen();
    fireEvent.press(screen.getByLabelText('멤버'));
    fireEvent.press(screen.getByLabelText(`사용자 #${OTHER_MEMBER_USER_ID} 강퇴`));

    expect(alertSpy).toHaveBeenCalled();
    expect(kickMutate).toHaveBeenCalledWith({ userId: OTHER_MEMBER_USER_ID }, expect.anything());
    expect(invalidateSpy).toHaveBeenCalledWith({ queryKey: MY_ROOMS_QUERY_KEY });
  });

  it('위임 확인 다이얼로그를 확정하면 방장 위임 mutation을 호출한다', () => {
    mockMembers({
      data: [
        member({ id: 1, userId: MY_USER_ID, role: 'HOST' }),
        member({ id: 2, userId: OTHER_MEMBER_USER_ID, role: 'MEMBER' }),
      ],
    });
    const transferMutate = jest.fn();
    useTransferHostMock.mockReturnValue({
      mutate: transferMutate,
      isPending: false,
    } as unknown as ReturnType<typeof useTransferHost>);
    jest.spyOn(Alert, 'alert').mockImplementation((_title, _message, buttons) => {
      buttons?.find((button) => button.text === '위임')?.onPress?.();
    });

    renderScreen();
    fireEvent.press(screen.getByLabelText('멤버'));
    fireEvent.press(screen.getByLabelText(`사용자 #${OTHER_MEMBER_USER_ID} 방장 위임`));

    expect(transferMutate).toHaveBeenCalledWith({ newHostUserId: OTHER_MEMBER_USER_ID });
  });

  it('탈퇴 시 확인 다이얼로그가 뜨고 확정 시 멤버에서 제거된다', () => {
    mockMembers({ data: [member({ id: 1, userId: MY_USER_ID, role: 'MEMBER' })] });
    const leaveMutate = jest.fn(() => {
      mockMembers({ data: [] });
    });
    useLeaveCommunityMock.mockReturnValue({
      mutate: leaveMutate,
      isPending: false,
    } as unknown as ReturnType<typeof useLeaveCommunity>);
    const alertSpy = jest.spyOn(Alert, 'alert').mockImplementation((_title, _message, buttons) => {
      buttons?.find((button) => button.text === '탈퇴')?.onPress?.();
    });

    const { rerenderScreen } = renderScreen();
    fireEvent.press(screen.getByLabelText('탈퇴하기'));

    expect(alertSpy).toHaveBeenCalled();
    expect(leaveMutate).toHaveBeenCalled();

    rerenderScreen();

    expect(screen.getByLabelText('가입하기')).toBeTruthy();
  });

  it('비멤버는 멤버 목록 접근 제한 안내를 본다', () => {
    const forbiddenError = new AxiosError('Forbidden', undefined, undefined, undefined, {
      status: 403,
      data: {},
      statusText: 'Forbidden',
      headers: {},
      config: {} as never,
    });
    mockMembers({ data: undefined, isError: true, error: forbiddenError });

    renderScreen();
    expect(screen.getByLabelText('가입하기')).toBeTruthy();

    fireEvent.press(screen.getByLabelText('멤버'));

    expect(screen.getByText('멤버 목록은 가입한 멤버만 볼 수 있어요')).toBeTruthy();
  });

  it('내 프로필 조회에 실패하면 가입하기 대신 에러 뷰가 표시되고 재시도하면 프로필을 다시 조회한다', () => {
    const refetch = jest.fn();
    mockMyProfile({ isError: true, error: new Error('boom'), data: undefined, refetch });

    renderScreen();

    expect(screen.queryByLabelText('가입하기')).toBeNull();
    fireEvent.press(screen.getByLabelText('다시 시도'));

    expect(refetch).toHaveBeenCalled();
  });

  it('잘못된 id로 접근하면 로딩이 무한 유지되지 않고 잘못된 접근 안내가 표시된다', () => {
    useLocalSearchParamsMock.mockReturnValue({ id: undefined } as unknown as { id: string });
    mockCommunity({ isLoading: true, data: undefined });
    mockMembers({ isLoading: true, data: undefined });
    mockMyProfile({ isLoading: true, data: undefined });

    renderScreen();

    expect(screen.queryByLabelText('로딩 중')).toBeNull();
    expect(screen.getByText('잘못된 접근이에요')).toBeTruthy();
  });

  it('다크 모드에서도 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');

    renderScreen();

    expect(screen.getAllByText(baseCommunity.name).length).toBeGreaterThan(0);
  });

  it('community.post.enabled·community.booking.enabled가 OFF면 게시판·활동 탭이 렌더되지 않는다', () => {
    isFeatureEnabledMock.mockReturnValue(false);

    renderScreen();

    expect(screen.queryByLabelText('게시판')).toBeNull();
    expect(screen.queryByLabelText('활동')).toBeNull();
    expect(screen.getByLabelText('소개')).toBeTruthy();
    expect(screen.getByLabelText('멤버')).toBeTruthy();
  });

  it('게시판 탭을 선택하면 communityId·ACTIVE 멤버 canWrite로 게시판 섹션이 렌더된다', () => {
    mockMembers({ data: [member({ id: 1, userId: MY_USER_ID, role: 'MEMBER' })] });

    renderScreen();
    fireEvent.press(screen.getByLabelText('게시판'));

    expect(
      screen.getByLabelText(`게시판 섹션 communityId=${baseCommunity.id} canWrite=true`)
    ).toBeTruthy();
  });

  it('게시판 섹션의 글쓰기 콜백을 탭하면 communityId 파라미터로 작성 화면에 이동한다', () => {
    mockMembers({ data: [member({ id: 1, userId: MY_USER_ID, role: 'MEMBER' })] });

    renderScreen();
    fireEvent.press(screen.getByLabelText('게시판'));
    fireEvent.press(
      screen.getByLabelText(`게시판 섹션 communityId=${baseCommunity.id} canWrite=true`)
    );

    expect(routerPushMock).toHaveBeenCalledWith(`/community/new?communityId=${baseCommunity.id}`);
    expect(routerPushMock).toHaveBeenCalledWith('/community/999');
  });

  it('활동 탭을 선택하면 방장만 canLink=true로 활동 섹션이 렌더된다', () => {
    mockMembers({ data: [member({ id: 1, userId: MY_USER_ID, role: 'HOST' })] });

    renderScreen();
    fireEvent.press(screen.getByLabelText('활동'));

    expect(
      screen.getByLabelText(`활동 섹션 communityId=${baseCommunity.id} canLink=true`)
    ).toBeTruthy();
  });

  it('활동 섹션의 연결하기 콜백을 탭하면 예약 연결 화면으로 이동한다', () => {
    mockMembers({ data: [member({ id: 1, userId: MY_USER_ID, role: 'HOST' })] });

    renderScreen();
    fireEvent.press(screen.getByLabelText('활동'));
    fireEvent.press(
      screen.getByLabelText(`활동 섹션 communityId=${baseCommunity.id} canLink=true`)
    );

    expect(routerPushMock).toHaveBeenCalledWith(`/communities/${baseCommunity.id}/bookings/new`);
  });

  it('일반 멤버는 활동 섹션에 canLink=false로 전달된다', () => {
    mockMembers({ data: [member({ id: 1, userId: MY_USER_ID, role: 'MEMBER' })] });

    renderScreen();
    fireEvent.press(screen.getByLabelText('활동'));

    expect(
      screen.getByLabelText(`활동 섹션 communityId=${baseCommunity.id} canLink=false`)
    ).toBeTruthy();
  });
});
