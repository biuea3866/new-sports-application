/**
 * RecruitmentApplicationsScreen(A-R4) — 신청자 목록(개설자 전용) 렌더·상태 검증.
 * 근거: design-fe-app.md Testing Plan에 준하는 4상태 표(0건 empty 정상 / 403 개설자만).
 *
 * useApplications를 모킹해 화면 배선만 검증한다.
 */
import React from 'react';
import { render, screen } from '@testing-library/react-native';
import mockUseColorScheme from 'react-native/Libraries/Utilities/useColorScheme';
import { AxiosError } from 'axios';

import type { ApplicationResponse } from '../../../../api/recruitment';
import RecruitmentApplicationsScreen from '../applications';

jest.mock('../../../../lib/useRecruitment', () => ({
  useApplications: jest.fn(),
}));

jest.mock('expo-router', () => ({
  useLocalSearchParams: jest.fn(),
}));

import { useLocalSearchParams } from 'expo-router';
import { useApplications } from '../../../../lib/useRecruitment';

const useApplicationsMock = useApplications as jest.MockedFunction<typeof useApplications>;
const useLocalSearchParamsMock = useLocalSearchParams as jest.MockedFunction<
  typeof useLocalSearchParams
>;

function mockApplications(overrides: Partial<ReturnType<typeof useApplications>>) {
  useApplicationsMock.mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    error: null,
    refetch: jest.fn(),
    ...overrides,
  } as unknown as ReturnType<typeof useApplications>);
}

const APPLICATION: ApplicationResponse = {
  id: 100,
  recruitmentId: 1,
  status: 'CONFIRMED',
  paymentId: 200,
  appliedAt: '2026-07-08T00:00:00+09:00',
};

describe('RecruitmentApplicationsScreen', () => {
  beforeEach(() => {
    mockUseColorScheme.mockReturnValue('light');
    useLocalSearchParamsMock.mockReturnValue({ id: '1' });
  });

  afterEach(() => {
    jest.clearAllMocks();
  });

  it('신청자 목록이 렌더된다', () => {
    mockApplications({ data: [APPLICATION] });

    render(<RecruitmentApplicationsScreen />);

    expect(screen.getByText(/신청 #100/)).toBeTruthy();
    expect(screen.getByText(/확정/)).toBeTruthy();
  });

  it('신청자가 0명이면 빈 상태가 렌더된다(정상)', () => {
    mockApplications({ data: [] });

    render(<RecruitmentApplicationsScreen />);

    expect(screen.getByText('아직 신청자가 없어요')).toBeTruthy();
  });

  it('개설자가 아니면(403) 안내가 표시된다', () => {
    const error = new AxiosError('Forbidden');
    error.response = { status: 403 } as AxiosError['response'];
    mockApplications({ isError: true, error });

    render(<RecruitmentApplicationsScreen />);

    expect(screen.getByText('개설자만 볼 수 있어요')).toBeTruthy();
  });

  it('다크 모드에서도 정상 렌더된다', () => {
    mockUseColorScheme.mockReturnValue('dark');
    mockApplications({ data: [APPLICATION] });

    render(<RecruitmentApplicationsScreen />);

    expect(screen.getByText(/신청 #100/)).toBeTruthy();
  });
});
