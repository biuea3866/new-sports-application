/**
 * RecruitmentsListScreen(A-R1) — 목록 렌더·4상태·개설 CTA 이동 검증.
 * 근거: design-fe-app.md Testing Plan "모집 목록"(목록 렌더 / 0건 empty / fetch 실패 error
 * / 개설 CTA 이동).
 *
 * useRecruitments를 모킹해 화면 배선만 사용자 관점으로 검증한다.
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import type { RecruitmentResponse } from '../../../api/recruitment';
import RecruitmentsListScreen from '../index';

jest.mock('../../../lib/useRecruitment', () => ({
  useRecruitments: jest.fn(),
}));

jest.mock('expo-router', () => ({
  router: { push: jest.fn(), replace: jest.fn() },
  useLocalSearchParams: jest.fn(() => ({})),
}));

import { router, useLocalSearchParams } from 'expo-router';
import { useRecruitments } from '../../../lib/useRecruitment';

const useRecruitmentsMock = useRecruitments as jest.MockedFunction<typeof useRecruitments>;
const useLocalSearchParamsMock = useLocalSearchParams as jest.MockedFunction<
  typeof useLocalSearchParams
>;

const OPEN_RECRUITMENT: RecruitmentResponse = {
  id: 1,
  title: '주말 축구 3명 모집',
  description: null,
  capacity: 3,
  feeAmount: 5000,
  activityAt: '2026-07-12T14:00:00+09:00',
  applicationDeadline: '2026-07-10T23:00:00+09:00',
  communityId: null,
  recruiterUserId: 10,
  status: 'OPEN',
};

function mockRecruitments(overrides: Partial<ReturnType<typeof useRecruitments>>) {
  useRecruitmentsMock.mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    refetch: jest.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useRecruitments>);
}

describe('RecruitmentsListScreen', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date('2026-07-08T00:00:00+09:00'));
    mockUseColorScheme.mockReturnValue('light');
    useLocalSearchParamsMock.mockReturnValue({});
  });

  afterEach(() => {
    jest.useRealTimers();
    jest.clearAllMocks();
  });

  it('모집 목록이 카드로 렌더된다', () => {
    mockRecruitments({ data: [OPEN_RECRUITMENT] });

    render(<RecruitmentsListScreen />);

    expect(screen.getByText('주말 축구 3명 모집')).toBeTruthy();
    expect(screen.getByText('정원 3 · 5,000원 · D-2 마감')).toBeTruthy();
  });

  it('목록이 비면 빈 상태가 렌더된다', () => {
    mockRecruitments({ data: [] });

    render(<RecruitmentsListScreen />);

    expect(screen.getByText('아직 모집이 없어요')).toBeTruthy();
  });

  it('조회 실패 시 ErrorView가 렌더되고 재시도를 탭하면 refetch가 호출된다', () => {
    const refetchMock = jest.fn();
    mockRecruitments({ isError: true, refetch: refetchMock });

    render(<RecruitmentsListScreen />);
    fireEvent.press(screen.getByLabelText('다시 시도'));

    expect(refetchMock).toHaveBeenCalled();
  });

  it('카드를 탭하면 모집 상세로 이동한다', () => {
    mockRecruitments({ data: [OPEN_RECRUITMENT] });

    render(<RecruitmentsListScreen />);
    fireEvent.press(screen.getByLabelText(/주말 축구 3명 모집/));

    expect(router.push).toHaveBeenCalledWith('/recruitments/1');
  });

  it('플로팅 개설 CTA를 탭하면 개설 화면으로 이동한다', () => {
    mockRecruitments({ data: [OPEN_RECRUITMENT] });

    render(<RecruitmentsListScreen />);
    fireEvent.press(screen.getByLabelText('모집 개설'));

    expect(router.push).toHaveBeenCalledWith('/recruitments/new');
  });

  it('다크 모드에서도 카드가 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');
    mockRecruitments({ data: [OPEN_RECRUITMENT] });

    render(<RecruitmentsListScreen />);

    expect(screen.getByText('주말 축구 3명 모집')).toBeTruthy();
  });
});
