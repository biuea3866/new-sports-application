/**
 * RecruitmentDetailScreen(A-R2) — 요약 렌더·정원마감 CTA disabled·신청→결제 이동·
 * 개설자 뷰 검증. 근거: design-fe-app.md Testing Plan "모집 상세".
 *
 * useRecruitment·useApplyRecruitment·useCancelRecruitment·useCurrentUserId를 모킹해
 * 화면 배선만 사용자 관점으로 검증한다.
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { AxiosError } from 'axios';

import type { RecruitmentResponse } from '../../../api/recruitment';
import RecruitmentDetailScreen from '../[id]';

jest.mock('../../../lib/useRecruitment', () => ({
  useRecruitment: jest.fn(),
  useApplyRecruitment: jest.fn(),
  useCancelRecruitment: jest.fn(),
}));

jest.mock('../../../api/goods', () => ({
  useCurrentUserId: jest.fn(),
}));

jest.mock('expo-router', () => ({
  router: { push: jest.fn(), replace: jest.fn() },
  useLocalSearchParams: jest.fn(),
}));

import { router, useLocalSearchParams } from 'expo-router';
import {
  useApplyRecruitment,
  useCancelRecruitment,
  useRecruitment,
} from '../../../lib/useRecruitment';
import { useCurrentUserId } from '../../../api/goods';

const useRecruitmentMock = useRecruitment as jest.MockedFunction<typeof useRecruitment>;
const useApplyRecruitmentMock = useApplyRecruitment as jest.MockedFunction<
  typeof useApplyRecruitment
>;
const useCancelRecruitmentMock = useCancelRecruitment as jest.MockedFunction<
  typeof useCancelRecruitment
>;
const useCurrentUserIdMock = useCurrentUserId as jest.MockedFunction<typeof useCurrentUserId>;
const useLocalSearchParamsMock = useLocalSearchParams as jest.MockedFunction<
  typeof useLocalSearchParams
>;

const RECRUITMENT: RecruitmentResponse = {
  id: 1,
  title: '주말 축구 3명 모집',
  description: '한강공원에서 진행합니다',
  capacity: 3,
  feeAmount: 5000,
  activityAt: '2026-07-12T14:00:00+09:00',
  applicationDeadline: '2026-07-10T23:00:00+09:00',
  communityId: 7,
  recruiterUserId: 10,
  status: 'OPEN',
};

function mockRecruitmentQuery(overrides: Partial<ReturnType<typeof useRecruitment>>) {
  useRecruitmentMock.mockReturnValue({
    data: RECRUITMENT,
    isLoading: false,
    isError: false,
    error: null,
    refetch: jest.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useRecruitment>);
}

function mockApply(
  mutate: jest.Mock,
  overrides: Partial<ReturnType<typeof useApplyRecruitment>> = {}
) {
  useApplyRecruitmentMock.mockReturnValue({
    mutate,
    isPending: false,
    ...overrides,
  } as unknown as ReturnType<typeof useApplyRecruitment>);
}

function mockCancelRecruitment(
  mutate: jest.Mock,
  overrides: Partial<ReturnType<typeof useCancelRecruitment>> = {}
) {
  useCancelRecruitmentMock.mockReturnValue({
    mutate,
    isPending: false,
    ...overrides,
  } as unknown as ReturnType<typeof useCancelRecruitment>);
}

describe('RecruitmentDetailScreen', () => {
  let applyMock: jest.Mock;
  let cancelMock: jest.Mock;

  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    useLocalSearchParamsMock.mockReturnValue({ id: '1' });
    useCurrentUserIdMock.mockReturnValue(1);
    mockRecruitmentQuery({});
    applyMock = jest.fn();
    mockApply(applyMock);
    cancelMock = jest.fn();
    mockCancelRecruitment(cancelMock);
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('요약 카드가 렌더된다', () => {
    render(<RecruitmentDetailScreen />);

    expect(screen.getByText('주말 축구 3명 모집')).toBeTruthy();
    expect(screen.getByText('5,000원')).toBeTruthy();
    expect(screen.getByText('3명')).toBeTruthy();
  });

  it('정원마감(CLOSED) 상태면 신청 CTA가 비활성화된다', () => {
    mockRecruitmentQuery({ data: { ...RECRUITMENT, status: 'CLOSED' } });

    render(<RecruitmentDetailScreen />);

    expect(screen.getByLabelText('모집 마감됨').props.accessibilityState.disabled).toBe(true);
  });

  it('신청하기를 탭하면 신청 후 결제 화면으로 이동한다', () => {
    applyMock.mockImplementation((_body, options) => {
      options?.onSuccess?.({
        id: 100,
        recruitmentId: 1,
        status: 'PENDING',
        paymentId: 200,
        checkoutUrl: 'https://mock-pg.example.com/checkout/abc',
        appliedAt: '2026-07-08T00:00:00+09:00',
      });
    });

    render(<RecruitmentDetailScreen />);
    fireEvent.press(screen.getByLabelText('신청하기 · 5,000원'));

    expect(router.push).toHaveBeenCalledWith(
      expect.stringContaining('/payment/new?orderType=RECRUITMENT')
    );
    expect(router.push).toHaveBeenCalledWith(expect.stringContaining('paymentId=200'));
    expect(router.push).toHaveBeenCalledWith(
      expect.stringContaining(
        `checkoutUrl=${encodeURIComponent('https://mock-pg.example.com/checkout/abc')}`
      )
    );
  });

  it('무료 신청(checkoutUrl 없음)이면 결제 화면 없이 내 신청 목록으로 이동한다', () => {
    applyMock.mockImplementation((_body, options) => {
      options?.onSuccess?.({
        id: 101,
        recruitmentId: 1,
        status: 'CONFIRMED',
        paymentId: null,
        checkoutUrl: null,
        appliedAt: '2026-07-08T00:00:00+09:00',
      });
    });
    mockRecruitmentQuery({ data: { ...RECRUITMENT, feeAmount: 0 } });

    render(<RecruitmentDetailScreen />);
    fireEvent.press(screen.getByLabelText('신청하기 · 무료'));

    expect(router.push).toHaveBeenCalledWith('/recruitments/me');
  });

  it('정원초과(409) 신청 실패 시 안내가 표시된다', () => {
    applyMock.mockImplementation((_body, options) => {
      const error = new AxiosError('Recruitment is full');
      error.response = { status: 409 } as AxiosError['response'];
      options?.onError?.(error);
    });

    render(<RecruitmentDetailScreen />);
    fireEvent.press(screen.getByLabelText('신청하기 · 5,000원'));

    expect(screen.getByText('마감됨')).toBeTruthy();
  });

  it('개설자 뷰에서는 신청자 보기·모집 취소 버튼이 노출된다', () => {
    useCurrentUserIdMock.mockReturnValue(10);

    render(<RecruitmentDetailScreen />);

    expect(screen.getByLabelText('신청자 보기')).toBeTruthy();
    expect(screen.getByLabelText('모집 취소')).toBeTruthy();
  });

  it('개설자가 아니면 신청자 보기·모집 취소 버튼이 노출되지 않는다', () => {
    useCurrentUserIdMock.mockReturnValue(1);

    render(<RecruitmentDetailScreen />);

    expect(screen.queryByLabelText('신청자 보기')).toBeNull();
    expect(screen.queryByLabelText('모집 취소')).toBeNull();
  });

  it('신청자 보기를 탭하면 신청자 목록 화면으로 이동한다', () => {
    useCurrentUserIdMock.mockReturnValue(10);

    render(<RecruitmentDetailScreen />);
    fireEvent.press(screen.getByLabelText('신청자 보기'));

    expect(router.push).toHaveBeenCalledWith('/recruitments/1/applications');
  });

  it('404(모집 없음)면 안내가 표시된다', () => {
    const error = new AxiosError('Not Found');
    error.response = { status: 404 } as AxiosError['response'];
    mockRecruitmentQuery({ data: undefined, isError: true, error });

    render(<RecruitmentDetailScreen />);

    expect(screen.getByText('삭제되었거나 없는 모집이에요')).toBeTruthy();
  });

  it('403(잠금)이면 잠금 안내가 표시된다', () => {
    const error = new AxiosError('Forbidden');
    error.response = { status: 403 } as AxiosError['response'];
    mockRecruitmentQuery({ data: undefined, isError: true, error });

    render(<RecruitmentDetailScreen />);

    expect(screen.getByText('이 모집은 모임 멤버만 볼 수 있어요')).toBeTruthy();
  });

  it('다크 모드에서도 요약 카드가 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');

    render(<RecruitmentDetailScreen />);

    expect(screen.getByText('주말 축구 3명 모집')).toBeTruthy();
  });
});
