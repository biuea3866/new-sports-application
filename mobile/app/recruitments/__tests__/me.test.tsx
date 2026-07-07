/**
 * MyApplicationsScreen(A-R5) — 내 신청 목록 렌더·4상태·취소 시트 진입 검증.
 * 근거: design-fe-app.md Testing Plan "취소 시트(A-R6)" 진입 지점, "화면별 4상태 표" A-R5.
 *
 * useMyApplications·useRecruitment(각 카드)·CancelApplicationSheet 배선만 사용자 관점으로
 * 검증한다(수수료 계산 자체는 lib/recruitment-cancellation 단위 테스트에서 검증됨).
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';

import type { ApplicationResponse, RecruitmentResponse } from '../../../api/recruitment';
import MyApplicationsScreen from '../me';

jest.mock('../../../lib/useRecruitment', () => ({
  useMyApplications: jest.fn(),
  useRecruitment: jest.fn(),
  useCancelApplication: jest.fn(),
}));

import {
  useCancelApplication,
  useMyApplications,
  useRecruitment,
} from '../../../lib/useRecruitment';

const useMyApplicationsMock = useMyApplications as jest.MockedFunction<typeof useMyApplications>;
const useRecruitmentMock = useRecruitment as jest.MockedFunction<typeof useRecruitment>;
const useCancelApplicationMock = useCancelApplication as jest.MockedFunction<
  typeof useCancelApplication
>;

const PENDING_APPLICATION: ApplicationResponse = {
  id: 100,
  recruitmentId: 1,
  status: 'PENDING',
  paymentId: 200,
  appliedAt: '2026-07-06T00:00:00+09:00',
};

const REFUNDED_APPLICATION: ApplicationResponse = {
  id: 101,
  recruitmentId: 2,
  status: 'REFUNDED',
  paymentId: 201,
  appliedAt: '2026-07-01T00:00:00+09:00',
};

const RECRUITMENT: RecruitmentResponse = {
  id: 1,
  title: '주말 축구 3명 모집',
  description: null,
  capacity: 3,
  feeAmount: 5000,
  activityAt: '2026-07-20T14:00:00+09:00',
  applicationDeadline: '2026-07-13T00:00:00+09:00',
  communityId: null,
  recruiterUserId: 10,
  status: 'OPEN',
};

function mockMyApplications(overrides: Partial<ReturnType<typeof useMyApplications>>) {
  useMyApplicationsMock.mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    refetch: jest.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useMyApplications>);
}

describe('MyApplicationsScreen', () => {
  beforeEach(() => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date('2026-07-08T00:00:00+09:00'));
    mockUseColorScheme.mockReturnValue('light');
    useRecruitmentMock.mockReturnValue({
      data: RECRUITMENT,
      isLoading: false,
    } as unknown as ReturnType<typeof useRecruitment>);
    useCancelApplicationMock.mockReturnValue({
      mutate: jest.fn(),
      isPending: false,
    } as unknown as ReturnType<typeof useCancelApplication>);
  });

  afterEach(() => {
    jest.useRealTimers();
    jest.clearAllMocks();
  });

  it('신청 목록이 카드로 렌더된다', () => {
    mockMyApplications({ data: [PENDING_APPLICATION] });

    render(<MyApplicationsScreen />);

    expect(screen.getByText('주말 축구 3명 모집')).toBeTruthy();
    expect(screen.getByText('대기 중')).toBeTruthy();
  });

  it('목록이 비면 빈 상태가 렌더된다', () => {
    mockMyApplications({ data: [] });

    render(<MyApplicationsScreen />);

    expect(screen.getByText('신청한 모집이 없어요')).toBeTruthy();
  });

  it('조회 실패 시 ErrorView가 렌더되고 재시도를 탭하면 refetch가 호출된다', () => {
    const refetchMock = jest.fn();
    mockMyApplications({ isError: true, refetch: refetchMock });

    render(<MyApplicationsScreen />);
    fireEvent.press(screen.getByLabelText('다시 시도'));

    expect(refetchMock).toHaveBeenCalled();
  });

  it('취소 가능한 신청(PENDING)에는 취소 CTA가 노출되고 탭하면 취소 시트가 열린다', () => {
    mockMyApplications({ data: [PENDING_APPLICATION] });

    render(<MyApplicationsScreen />);
    fireEvent.press(screen.getByLabelText('취소'));

    expect(screen.getByText('신청을 취소할까요?')).toBeTruthy();
  });

  it('종료된 신청(REFUNDED)에는 취소 CTA가 노출되지 않는다', () => {
    mockMyApplications({ data: [REFUNDED_APPLICATION] });

    render(<MyApplicationsScreen />);

    expect(screen.queryByLabelText('취소')).toBeNull();
    expect(screen.getByText('환불됨')).toBeTruthy();
  });

  it('다크 모드에서도 카드가 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');
    mockMyApplications({ data: [PENDING_APPLICATION] });

    render(<MyApplicationsScreen />);

    expect(screen.getByText('주말 축구 3명 모집')).toBeTruthy();
  });
});
