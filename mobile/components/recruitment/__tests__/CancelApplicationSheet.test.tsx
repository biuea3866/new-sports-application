/**
 * CancelApplicationSheet(A-R6) — 신청 취소 수수료 미리보기·CTA 분기 검증.
 * 근거: design-fe-app.md Testing Plan "취소 시트(A-R6)".
 *
 * useRecruitment·useCancelApplication을 모킹해 미리보기 계산 배선(수수료 표시·마감후
 * disabled·오류 안내)만 사용자 관점으로 검증한다. 시각은 jest 가짜 타이머로 고정한다
 * (`2026-07-08T00:00:00+09:00` — useCountdown 테스트 선례와 동일 패턴).
 */
import React from 'react';
import { fireEvent, render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { AxiosError } from 'axios';

import type { ApplicationResponse, RecruitmentResponse } from '../../../api/recruitment';
import { CancelApplicationSheet } from '../CancelApplicationSheet';

jest.mock('../../../lib/useRecruitment', () => ({
  useRecruitment: jest.fn(),
  useCancelApplication: jest.fn(),
}));

import { useCancelApplication, useRecruitment } from '../../../lib/useRecruitment';

const useRecruitmentMock = useRecruitment as jest.MockedFunction<typeof useRecruitment>;
const useCancelApplicationMock = useCancelApplication as jest.MockedFunction<
  typeof useCancelApplication
>;

const APPLICATION: ApplicationResponse = {
  id: 100,
  recruitmentId: 1,
  status: 'PENDING',
  paymentId: 200,
  appliedAt: '2026-07-06T00:00:00+09:00',
};

function mockRecruitment(overrides: Partial<RecruitmentResponse>) {
  const recruitment: RecruitmentResponse = {
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
    ...overrides,
  };
  useRecruitmentMock.mockReturnValue({
    data: recruitment,
    isLoading: false,
  } as unknown as ReturnType<typeof useRecruitment>);
}

function mockCancelApplication(
  mutate: jest.Mock,
  overrides: Partial<ReturnType<typeof useCancelApplication>> = {}
) {
  useCancelApplicationMock.mockReturnValue({
    mutate,
    isPending: false,
    ...overrides,
  } as unknown as ReturnType<typeof useCancelApplication>);
}

describe('CancelApplicationSheet', () => {
  let cancelMock: jest.Mock;
  const onClose = jest.fn();

  beforeEach(() => {
    jest.useFakeTimers();
    jest.setSystemTime(new Date('2026-07-08T00:00:00+09:00'));
    mockUseColorScheme.mockReturnValue('light');
    cancelMock = jest.fn();
    mockCancelApplication(cancelMock);
  });

  afterEach(() => {
    jest.useRealTimers();
    jest.clearAllMocks();
  });

  it('5% 구간이면 환불액 4,750원과 수수료 250원 공제 안내가 표시된다', () => {
    mockRecruitment({ applicationDeadline: '2026-07-13T00:00:00+09:00' });

    render(<CancelApplicationSheet application={APPLICATION} onClose={onClose} />);

    expect(screen.getByText('환불 예정 4,750원')).toBeTruthy();
    expect(screen.getByText('수수료 5% (250원) 공제')).toBeTruthy();
  });

  it('7일 초과(무료 구간)면 수수료 없음 안내가 표시된다', () => {
    mockRecruitment({ applicationDeadline: '2026-07-20T00:00:00+09:00' });

    render(<CancelApplicationSheet application={APPLICATION} onClose={onClose} />);

    expect(screen.getByText('환불 예정 5,000원')).toBeTruthy();
    expect(screen.getByText('전액 환불 · 수수료 없음')).toBeTruthy();
  });

  it('마감 후에는 취소할 수 없다는 안내와 함께 취소 CTA가 비활성화된다', () => {
    mockRecruitment({ applicationDeadline: '2026-07-01T00:00:00+09:00' });

    render(<CancelApplicationSheet application={APPLICATION} onClose={onClose} />);

    expect(screen.getByText('마감되어 취소할 수 없어요')).toBeTruthy();
    expect(screen.getByLabelText('취소하기').props.accessibilityState.disabled).toBe(true);
  });

  it('취소하기를 탭하면 신청 취소 mutation이 호출된다', () => {
    mockRecruitment({ applicationDeadline: '2026-07-13T00:00:00+09:00' });

    render(<CancelApplicationSheet application={APPLICATION} onClose={onClose} />);
    fireEvent.press(screen.getByLabelText('취소하기'));

    expect(cancelMock).toHaveBeenCalledWith(100, expect.anything());
  });

  it('PG 오류(500)면 재시도 안내 문구가 표시된다', () => {
    mockRecruitment({ applicationDeadline: '2026-07-13T00:00:00+09:00' });
    cancelMock.mockImplementation((_id, options) => {
      const error = new AxiosError('Internal Server Error');
      error.response = { status: 500 } as AxiosError['response'];
      options?.onError?.(error);
    });

    render(<CancelApplicationSheet application={APPLICATION} onClose={onClose} />);
    fireEvent.press(screen.getByLabelText('취소하기'));

    expect(screen.getByText('환불 처리 실패, 잠시 후 다시 시도해주세요')).toBeTruthy();
  });

  it('닫기를 탭하면 onClose가 호출된다', () => {
    mockRecruitment({ applicationDeadline: '2026-07-13T00:00:00+09:00' });

    render(<CancelApplicationSheet application={APPLICATION} onClose={onClose} />);
    fireEvent.press(screen.getByLabelText('닫기'));

    expect(onClose).toHaveBeenCalled();
  });

  it('다크 모드에서도 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');
    mockRecruitment({ applicationDeadline: '2026-07-13T00:00:00+09:00' });

    render(<CancelApplicationSheet application={APPLICATION} onClose={onClose} />);

    expect(screen.getByText('환불 예정 4,750원')).toBeTruthy();
  });
});
